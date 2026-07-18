//! Android native payload extractor — JNI shared library.

use jni::objects::{JByteArray, JClass, JString};
use jni::strings::JNIString;
use jni::sys::jlong;
use jni::EnvUnowned;
use sha2::{Digest, Sha256};
use std::fs;
use std::io::BufReader;
use std::os::unix::io::AsRawFd;

const ZIP_LOCAL_HEADER: u32 = 0x0403_4b50;
const ZIP_CENTRAL_HEADER: u32 = 0x0201_4b50;
const ZIP_END_HEADER: u32 = 0x0605_4b50;

// ── byte helpers ─────────────────────────────────────────────────────────────

fn read_u16(buf: &[u8]) -> u32 {
    u32::from_le_bytes([buf[0], buf[1], 0, 0])
}

fn read_u32(buf: &[u8]) -> u32 {
    u32::from_le_bytes([buf[0], buf[1], buf[2], buf[3]])
}

fn read_exact_at(fd: i32, mut offset: u64, buf: &mut [u8]) -> std::io::Result<()> {
    let mut off: usize = 0;
    let mut remaining = buf.len();
    while remaining > 0 {
        let count = unsafe {
            libc::pread(
                fd,
                buf[off..].as_mut_ptr() as *mut libc::c_void,
                remaining,
                offset as libc::off_t,
            )
        };
        if count < 0 {
            let err = std::io::Error::last_os_error();
            if err.kind() == std::io::ErrorKind::Interrupted {
                continue;
            }
            return Err(err);
        }
        if count == 0 {
            return Err(std::io::Error::new(
                std::io::ErrorKind::UnexpectedEof,
                "short read",
            ));
        }
        let n = count as usize;
        off += n;
        offset += n as u64;
        remaining -= n;
    }
    Ok(())
}

fn write_all(fd: i32, mut buf: &[u8]) -> std::io::Result<()> {
    while !buf.is_empty() {
        let count = unsafe { libc::write(fd, buf.as_ptr() as *const libc::c_void, buf.len()) };
        if count < 0 {
            let err = std::io::Error::last_os_error();
            if err.kind() == std::io::ErrorKind::Interrupted {
                continue;
            }
            return Err(err);
        }
        if count == 0 {
            return Err(std::io::Error::new(
                std::io::ErrorKind::WriteZero,
                "write failed",
            ));
        }
        buf = &buf[count as usize..];
    }
    Ok(())
}

// ── ZIP archive ──────────────────────────────────────────────────────────────

struct ZipEntryLocation {
    data_offset: u64,
    compressed_size: u64,
}

struct NativeArchive {
    fd: i32,
    file_size: u64,
    central_directory: Vec<u8>,
    entry_count: u32,
}

impl NativeArchive {
    fn open(path: &str) -> Result<Self, String> {
        let file = fs::File::open(path).map_err(|e| format!("open APK: {e}"))?;
        let meta = file.metadata().map_err(|e| format!("invalid APK: {e}"))?;
        let file_size = meta.len();
        if file_size < 22 {
            return Err("invalid APK: too small".into());
        }
        let fd = file.as_raw_fd();
        let tail_size = file_size.min(65557) as usize;
        let mut tail = vec![0u8; tail_size];
        read_exact_at(fd, file_size - tail_size as u64, &mut tail)
            .map_err(|e| format!("read APK ZIP footer: {e}"))?;
        let mut end: Option<usize> = None;
        let mut off = tail_size.saturating_sub(22);
        loop {
            if read_u32(&tail[off..]) == ZIP_END_HEADER
                && off as u64 + 22 + read_u16(&tail[off + 20..]) as u64 == tail_size as u64
            {
                end = Some(off);
                break;
            }
            if off == 0 {
                break;
            }
            off -= 1;
        }
        let end = end.ok_or("APK ZIP end record is missing")?;
        let entry_count = read_u16(&tail[end + 10..]);
        let central_size = read_u32(&tail[end + 12..]) as u64;
        let central_offset = read_u32(&tail[end + 16..]) as u64;
        if entry_count == 0xffff
            || central_size == 0xffff_ffff
            || central_offset == 0xffff_ffff
            || central_offset + central_size > file_size
        {
            return Err("unsupported or invalid ZIP64 APK".into());
        }
        let mut central_directory = vec![0u8; central_size as usize];
        read_exact_at(fd, central_offset, &mut central_directory)
            .map_err(|e| format!("read ZIP central directory: {e}"))?;
        std::mem::forget(file);
        Ok(NativeArchive {
            fd,
            file_size,
            central_directory,
            entry_count,
        })
    }
    fn locate_entry(&self, entry_name: &str) -> Result<ZipEntryLocation, String> {
        let wanted = entry_name.as_bytes();
        let mut cursor: usize = 0;
        for _ in 0..self.entry_count {
            if cursor + 46 > self.central_directory.len() {
                return Err("invalid ZIP central directory bounds".into());
            }
            let header = &self.central_directory[cursor..];
            if read_u32(header) != ZIP_CENTRAL_HEADER {
                return Err("invalid ZIP central directory entry".into());
            }
            let method = read_u16(&header[10..]);
            let compressed_size = read_u32(&header[20..]) as u64;
            let name_length = read_u16(&header[28..]) as usize;
            let extra_length = read_u16(&header[30..]) as usize;
            let comment_length = read_u16(&header[32..]) as usize;
            let local_offset = read_u32(&header[42..]) as u64;
            let next = cursor + 46 + name_length + extra_length + comment_length;
            if next > self.central_directory.len() {
                return Err("invalid ZIP central directory bounds".into());
            }
            if name_length == wanted.len() && &header[46..46 + name_length] == wanted {
                if method != 0 {
                    return Err(format!("payload ZIP entry is not Stored: {entry_name}"));
                }
                if local_offset == 0xffff_ffff {
                    return Err(format!("invalid ZIP local header for {entry_name}"));
                }
                let mut local = [0u8; 30];
                read_exact_at(self.fd, local_offset, &mut local)
                    .map_err(|_| format!("invalid ZIP local header for {entry_name}"))?;
                if read_u32(&local) != ZIP_LOCAL_HEADER {
                    return Err(format!("invalid ZIP local header for {entry_name}"));
                }
                let data_offset =
                    local_offset + 30 + read_u16(&local[26..]) as u64 + read_u16(&local[28..]) as u64;
                if data_offset + compressed_size > self.file_size {
                    return Err(format!("invalid ZIP payload bounds for {entry_name}"));
                }
                return Ok(ZipEntryLocation {
                    data_offset,
                    compressed_size,
                });
            }
            cursor = next;
        }
        Err(format!("payload entry is missing: {entry_name}"))
    }
}

impl Drop for NativeArchive {
    fn drop(&mut self) {
        unsafe { libc::close(self.fd) };
    }
}

// ── extraction ───────────────────────────────────────────────────────────────

fn extract_payload(
    archive: &NativeArchive,
    entry_name: &str,
    target_path: &str,
    expected_size: u64,
    expected_sha256: &[u8; 32],
) -> Result<(), String> {
    let location = archive.locate_entry(entry_name)?;
    let c_path = std::ffi::CString::new(target_path).map_err(|_| "invalid target path")?;
    let output_fd = unsafe {
        libc::open(
            c_path.as_ptr(),
            libc::O_WRONLY | libc::O_CREAT | libc::O_EXCL | libc::O_CLOEXEC,
            0o600,
        )
    };
    if output_fd < 0 {
        return Err(format!("create payload: {}", std::io::Error::last_os_error()));
    }
    let result = (|| -> Result<(), String> {
        let mut compressed = vec![0u8; location.compressed_size as usize];
        read_exact_at(archive.fd, location.data_offset, &mut compressed)
            .map_err(|e| format!("read compressed payload: {e}"))?;
        let mut reader = BufReader::new(compressed.as_slice());
        let mut decompressed = Vec::new();
        lzma_rs::xz_decompress(&mut reader, &mut decompressed)
            .map_err(|e| format!("decompress payload: {e}"))?;
        write_all(output_fd, &decompressed)
            .map_err(|e| format!("write payload: {e}"))?;
        let mut sha = Sha256::new();
        sha.update(&decompressed);
        let actual_sha256 = sha.finalize();
        let output_size = decompressed.len() as u64;
        if output_size != expected_size || actual_sha256.as_slice() != expected_sha256 {
            return Err(format!(
                "payload verification failed: size {output_size}, expected {expected_size}"
            ));
        }
        if unsafe { libc::fsync(output_fd) } != 0 {
            return Err(format!("sync payload: {}", std::io::Error::last_os_error()));
        }
        Ok(())
    })();
    unsafe { libc::close(output_fd) };
    if result.is_err() {
        let _ = fs::remove_file(target_path);
    }
    result
}

// ── JNI entry points ────────────────────────────────────────────────────────

fn throw_io_exception(env: &mut jni::Env, message: &str) {
    if env.exception_check() {
        return;
    }
    let class = JNIString::from("java/io/IOException");
    let msg = JNIString::from(message);
    let _ = env.throw_new(class, msg);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_flycat_loader_NativePayloadExtractor_openArchive<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    apk_path_value: JString<'local>,
) -> jlong {
    env.with_env(|env| {
        let apk_path = match apk_path_value.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                throw_io_exception(env, "APK path is missing");
                return Ok::< jlong, jni::errors::Error>(0);
            }
        };
        match NativeArchive::open(&apk_path) {
            Ok(archive) => Ok(Box::into_raw(Box::new(archive)) as jlong),
            Err(e) => {
                throw_io_exception(env, &e);
                Ok(0)
            }
        }
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_flycat_loader_NativePayloadExtractor_extract<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    archive_value: jlong,
    entry_name_value: JString<'local>,
    target_path_value: JString<'local>,
    expected_size: jlong,
    expected_sha256_value: JByteArray<'local>,
) {
    let _ = env.with_env::<_, (), jni::errors::Error>(|env| {
        if archive_value == 0 {
            throw_io_exception(env, "invalid payload metadata");
            return Ok(());
        }
        let archive = unsafe { &*(archive_value as *const NativeArchive) };
        if expected_sha256_value.len(env)? != 32 {
            throw_io_exception(env, "invalid payload metadata");
            return Ok(());
        }
        let entry_name = match entry_name_value.try_to_string(env) {
            Ok(s) => s,
            Err(_) => return Ok(()),
        };
        let target_path = match target_path_value.try_to_string(env) {
            Ok(s) => s,
            Err(_) => return Ok(()),
        };
        let mut sha256_jbyte = [0i8; 32];
        expected_sha256_value.get_region(env, 0, &mut sha256_jbyte)?;
        let sha256: [u8; 32] = unsafe { std::mem::transmute(sha256_jbyte) };
        if let Err(e) = extract_payload(archive, &entry_name, &target_path, expected_size as u64, &sha256) {
            throw_io_exception(env, &e);
        }
        Ok(())
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_flycat_loader_NativePayloadExtractor_closeArchive(
    _env: EnvUnowned<'_>,
    _class: JClass<'_>,
    archive_value: jlong,
) {
    if archive_value != 0 {
        drop(unsafe { Box::from_raw(archive_value as *mut NativeArchive) });
    }
}
