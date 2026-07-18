package dev.flycat.loader;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

final class PayloadInstaller {
    private static volatile Installation installedPayload;
    private static final String CACHE_MARKER = ".complete";

    private PayloadInstaller() {}

    static Installation install(ApplicationInfo appInfo, ClassLoader parentLoader, Object loadedApk) {
        Installation current = installedPayload;
        if (current != null) {
            return current;
        }
        synchronized (PayloadInstaller.class) {
            if (installedPayload != null) {
                return installedPayload;
            }
            ReflectionAccess.exemptHiddenApis();
            try (ZipFile apk = new ZipFile(appInfo.sourceDir)) {
                PayloadMetadata metadata = readMetadata(apk);
                File root = new File(appInfo.dataDir, "code_cache/loader");
                ensureDirectory(root);
                try (RandomAccessFile lockFile = new RandomAccessFile(new File(root, "install.lock"), "rw");
                     java.nio.channels.FileLock ignored = lockFile.getChannel().lock()) {
                    File payloadDir = new File(root, metadata.id);
                    File dexDir = new File(payloadDir, "dex");
                    ensureDirectory(dexDir);

                    List<PayloadMetadata.NativeEntry> nativeEntries = selectNativeEntries(metadata.nativeEntries);
                    File nativeDir = null;
                    if (!nativeEntries.isEmpty()) {
                        nativeDir = new File(payloadDir, "lib/" + nativeEntries.get(0).abi);
                        ensureDirectory(nativeDir);
                    }
                    File completeMarker = new File(payloadDir, CACHE_MARKER);
                    boolean cacheComplete = hasCompleteMarker(completeMarker, metadata.id)
                            && hasExpectedPayloadFiles(metadata.dexEntries, dexDir, nativeEntries, nativeDir);
                    if (completeMarker.exists() && !cacheComplete && !completeMarker.delete()) {
                        throw new IOException("Unable to invalidate incomplete payload cache: " + completeMarker);
                    }

                    long archive = cacheComplete ? 0 : NativePayloadExtractor.openArchive(apk.getName());
                    List<File> dexFiles = new ArrayList<>(metadata.dexEntries.size());
                    try {
                        for (PayloadMetadata.DexEntry entry : metadata.dexEntries) {
                            File target = new File(dexDir, entry.outputName);
                            if (!cacheComplete && !isValid(target, entry.size, entry.sha256)) {
                                extract(archive, entry.assetName, entry.size, entry.sha256, target);
                            }
                            makeReadOnly(target);
                            dexFiles.add(target);
                        }
                        if (!nativeEntries.isEmpty()) {
                            for (PayloadMetadata.NativeEntry entry : nativeEntries) {
                                File target = new File(nativeDir, entry.outputName);
                                if (!cacheComplete && !isValid(target, entry.size, entry.sha256)) {
                                    extract(archive, entry.assetName, entry.size, entry.sha256, target);
                                }
                                makeReadOnly(target);
                            }
                        }
                    } finally {
                        if (archive != 0) {
                            NativePayloadExtractor.closeArchive(archive);
                        }
                    }
                    if (!cacheComplete) {
                        publishCompleteMarker(payloadDir, metadata.id, completeMarker);
                    }
                    ClassLoader payloadLoader = createPayloadLoader(
                            parentLoader,
                            dexFiles,
                            nativeDir
                    );
                    RuntimeBootstrap.installClassLoader(loadedApk, payloadLoader);
                    installedPayload = new Installation(metadata, payloadLoader);
                }
                return installedPayload;
            } catch (IOException | ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to install the application payload", error);
            }
        }
    }

    private static PayloadMetadata readMetadata(ZipFile apk) throws IOException {
        ZipEntry entry = apk.getEntry(PayloadMetadata.METADATA_ENTRY);
        if (entry == null) {
            throw new IOException("Loader payload metadata is missing");
        }
        try (java.io.InputStream input = apk.getInputStream(entry)) {
            return PayloadMetadata.read(input);
        }
    }

    private static ClassLoader createPayloadLoader(
            ClassLoader parentLoader,
            List<File> dexFiles,
            File nativeDir
    ) {
        StringBuilder dexPath = new StringBuilder();
        for (File file : dexFiles) {
            if (dexPath.length() > 0) {
                dexPath.append(File.pathSeparatorChar);
            }
            dexPath.append(file.getAbsolutePath());
        }
        DexClassLoader payloadLoader = new DexClassLoader(
                dexPath.toString(),
                null,
                nativeDir == null ? null : nativeDir.getAbsolutePath(),
                parentLoader
        );
        Thread.currentThread().setContextClassLoader(payloadLoader);
        return payloadLoader;
    }

    private static void extract(
            long archive,
            String assetName,
            long expectedSize,
            byte[] expectedSha256,
            File target
    )
            throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp-" + android.os.Process.myPid());
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Unable to remove stale temporary payload: " + temporary);
        }
        try {
            NativePayloadExtractor.extract(
                    archive,
                    assetName,
                    temporary.getAbsolutePath(),
                    expectedSize,
                    expectedSha256
            );
        } catch (Throwable error) {
            temporary.delete();
            throw error;
        }
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IOException("Unable to replace stale payload: " + target);
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Unable to publish extracted payload: " + target);
        }
    }

    private static boolean isValid(File file, long expectedSize, byte[] expectedSha256) throws IOException {
        if (!file.isFile() || file.length() != expectedSize) {
            return false;
        }
        MessageDigest digest = sha256Digest();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return Arrays.equals(digest.digest(), expectedSha256);
    }

    private static boolean hasExpectedPayloadFiles(
            List<PayloadMetadata.DexEntry> dexEntries,
            File dexDir,
            List<PayloadMetadata.NativeEntry> nativeEntries,
            File nativeDir
    ) {
        for (PayloadMetadata.DexEntry entry : dexEntries) {
            if (!hasExpectedSize(new File(dexDir, entry.outputName), entry.size)) {
                return false;
            }
        }
        for (PayloadMetadata.NativeEntry entry : nativeEntries) {
            if (!hasExpectedSize(new File(nativeDir, entry.outputName), entry.size)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasCompleteMarker(File marker, String payloadId) {
        byte[] expected = payloadId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (!marker.isFile() || marker.length() != expected.length) {
            return false;
        }
        byte[] actual = new byte[expected.length];
        try (FileInputStream input = new FileInputStream(marker)) {
            int offset = 0;
            while (offset < actual.length) {
                int read = input.read(actual, offset, actual.length - offset);
                if (read < 0) {
                    return false;
                }
                offset += read;
            }
            return input.read() == -1 && Arrays.equals(actual, expected);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean hasExpectedSize(File file, long expectedSize) {
        return file.isFile() && file.length() == expectedSize;
    }

    private static void publishCompleteMarker(File payloadDir, String payloadId, File target)
            throws IOException {
        File temporary = new File(payloadDir, CACHE_MARKER + ".tmp-" + android.os.Process.myPid());
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Unable to remove stale cache marker: " + temporary);
        }
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(payloadId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.getFD().sync();
        } catch (Throwable error) {
            temporary.delete();
            throw error;
        }
        makeReadOnly(temporary);
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IOException("Unable to replace payload cache marker: " + target);
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Unable to publish payload cache marker: " + target);
        }
    }

    private static void makeReadOnly(File file) throws IOException {
        if (file.canWrite() && !file.setReadOnly()) {
            throw new IOException("Unable to make payload read-only: " + file);
        }
        if (file.canWrite()) {
            throw new IOException("Payload remains writable: " + file);
        }
    }

    private static List<PayloadMetadata.NativeEntry> selectNativeEntries(
            List<PayloadMetadata.NativeEntry> entries
    ) throws IOException {
        if (entries.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        for (String abi : Build.SUPPORTED_ABIS) {
            List<PayloadMetadata.NativeEntry> selected = new ArrayList<>();
            for (PayloadMetadata.NativeEntry entry : entries) {
                if (abi.equals(entry.abi)) {
                    selected.add(entry);
                }
            }
            if (!selected.isEmpty()) {
                return selected;
            }
        }
        throw new IOException("Payload has no native libraries for " + Arrays.toString(Build.SUPPORTED_ABIS));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Unable to create directory: " + directory);
        }
    }

    static final class Installation {
        final PayloadMetadata metadata;
        final ClassLoader classLoader;

        Installation(PayloadMetadata metadata, ClassLoader classLoader) {
            this.metadata = metadata;
            this.classLoader = classLoader;
        }
    }
}
