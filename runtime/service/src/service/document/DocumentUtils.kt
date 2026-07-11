package com.github.yumelira.yumebox.runtime.service.document

import android.content.Context
import android.provider.DocumentsContract
import com.github.yumelira.yumebox.core.importedDir
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.runtime.service.R
import com.github.yumelira.yumebox.runtime.service.runtime.records.ImportedDao
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

enum class Flag { Writable, Deletable, Virtual }

interface Document {
    val id: String
    val name: String
    val mimeType: String
    val size: Long
    val updatedAt: Long
    val flags: Set<Flag>
}

class FileDocument(
    val file: File,
    override val flags: Set<Flag>,
    private val idOverride: String? = null,
    private val nameOverride: String? = null,
) : Document {
    override val id: String get() = idOverride ?: file.name
    override val name: String get() = nameOverride ?: file.name
    override val mimeType: String
        get() = if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "text/plain"
    override val size: Long get() = file.length()
    override val updatedAt: Long get() = file.lastModified()
}

class VirtualDocument(
    override val id: String,
    override val name: String,
    override val mimeType: String,
    override val size: Long,
    override val updatedAt: Long,
    override val flags: Set<Flag>,
) : Document

data class Path(
    val uuid: UUID?,
    val scope: Scope?,
    val relative: List<String>?,
) {
    enum class Scope { Runtime, Configuration, Providers }
    override fun toString(): String {
        if (uuid == null) {
            if (scope == null) return "/"
            if (scope == Scope.Runtime) {
                if (relative == null) return "/${Paths.RUNTIME_ID}"
                return "/${Paths.RUNTIME_ID}/${relative.joinToString(separator = "/")}"
            }
            throw IllegalStateException("invalid path state without uuid: scope=$scope")
        }
        if (scope == null) return "/$uuid"
        val sc = when (scope) {
            Scope.Runtime -> throw IllegalStateException("runtime scope should not contain uuid")
            Scope.Configuration -> Paths.CONFIGURATION_ID
            Scope.Providers -> Paths.PROVIDERS_ID
        }
        if (relative == null) return "/$uuid/$sc"
        return "/$uuid/$sc/${relative.joinToString(separator = "/")}"
    }
}

object Paths {
    const val RUNTIME_ID = "runtime"
    const val CONFIGURATION_ID = "config.yaml"
    const val PROVIDERS_ID = "providers"
    fun resolve(path: String): Path {
        val segments = path.split("/").filter { it.isNotBlank() && it != "." && it != ".." }
        return when (segments.size) {
            0 -> Path(uuid = null, scope = null, relative = null)
            1 -> {
                if (segments[0] == RUNTIME_ID) {
                    Path(uuid = null, scope = Path.Scope.Runtime, relative = null)
                } else {
                    Path(uuid = UUID.fromString(segments[0]), scope = null, relative = null)
                }
            }
            2 -> Path(
                uuid = if (segments[0] == RUNTIME_ID) null else UUID.fromString(segments[0]),
                scope = if (segments[0] == RUNTIME_ID) {
                    Path.Scope.Runtime
                } else {
                    when (segments[1]) {
                        CONFIGURATION_ID -> Path.Scope.Configuration
                        PROVIDERS_ID -> Path.Scope.Providers
                        else -> throw IllegalArgumentException("unknown scope ${segments[1]}")
                    }
                },
                relative = if (segments[0] == RUNTIME_ID) segments.drop(1) else null,
            )
            else -> Path(
                uuid = if (segments[0] == RUNTIME_ID) null else UUID.fromString(segments[0]),
                scope = if (segments[0] == RUNTIME_ID) {
                    Path.Scope.Runtime
                } else {
                    when (segments[1]) {
                        CONFIGURATION_ID -> Path.Scope.Configuration
                        PROVIDERS_ID -> Path.Scope.Providers
                        else -> throw IllegalArgumentException("unknown scope ${segments[1]}")
                    }
                },
                relative = if (segments[0] == RUNTIME_ID) segments.drop(1) else segments.drop(2),
            )
        }
    }
}

class Picker(private val context: Context) {
    fun list(path: Path): List<Document> {
        if (path.uuid == null && path.scope == null) {
            return buildList {
                add(pick(Path(uuid = null, scope = Path.Scope.Runtime, relative = null), false))
                addAll(
                    ImportedDao.queryAllUUIDs().map {
                        pick(path.copy(uuid = it), false)
                    }
                )
            }
        }
        if (path.uuid == null && path.scope == Path.Scope.Runtime) {
            val parent = pick(path, false)
            if (parent !is FileDocument)
                return emptyList()
            return (parent.file.list() ?: emptyArray()).map {
                pick(path.copy(relative = (path.relative ?: emptyList()) + it), false)
            }
        }
        if (path.scope == null) {
            return listOf(Path.Scope.Configuration, Path.Scope.Providers).map {
                pick(path.copy(scope = it), false)
            }
        }
        val parent = pick(path, false)
        if (parent !is FileDocument)
            return emptyList()
        return (parent.file.list() ?: emptyArray()).map {
            pick(path.copy(relative = (path.relative ?: emptyList()) + it), false)
        }
    }
    fun pick(path: Path, writable: Boolean): Document {
        if (path.uuid == null && path.scope == null) {
            return VirtualDocument(
                id = "",
                name = context.getString(R.string.files_provider_title),
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                size = 0,
                updatedAt = 0,
                flags = setOf(Flag.Virtual),
            )
        }
        if (path.uuid == null && path.scope == Path.Scope.Runtime) {
            if (writable)
                throw IllegalArgumentException("runtime directory is read-only")
            val runtimeDir = context.runtimeHomeDir
            if (path.relative == null) {
                return FileDocument(
                    file = runtimeDir,
                    flags = setOf(Flag.Virtual),
                    idOverride = Paths.RUNTIME_ID,
                    nameOverride = context.getString(R.string.files_provider_runtime),
                )
            }
            return FileDocument(
                file = runtimeDir.resolve(path.relative.joinToString(separator = "/")),
                flags = emptySet(),
            )
        }
        val profileUuid: UUID = path.uuid
            ?: throw FileNotFoundException("invalid path: missing profile uuid")
        val imported = ImportedDao.queryByUUID(profileUuid)
            ?: throw FileNotFoundException("profile not found: $profileUuid")
        if (path.scope == null) {
            if (writable)
                throw IllegalArgumentException("invalid open mode for profile root")
            return VirtualDocument(
                id = path.uuid.toString(),
                name = imported.name,
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                size = 0,
                updatedAt = 0,
                flags = setOf(Flag.Virtual),
            )
        }
        val profileDir = context.importedDir.resolve(imported.uuid.toString())
        if (path.relative == null) {
            if (path.scope == Path.Scope.Configuration) {
                val isWritable = imported.type == Profile.Type.File
                if (writable && !isWritable)
                    throw IllegalArgumentException("profile is not a file type, cannot write config.yaml")
                return FileDocument(
                    file = profileDir.resolve("config.yaml"),
                    flags = if (isWritable) setOf(Flag.Writable) else emptySet(),
                    idOverride = Paths.CONFIGURATION_ID,
                    nameOverride = context.getString(R.string.files_provider_config_yaml),
                )
            } else {
                return FileDocument(
                    file = profileDir.resolve("providers"),
                    idOverride = Paths.PROVIDERS_ID,
                    nameOverride = context.getString(R.string.files_provider_providers),
                    flags = setOf(Flag.Virtual),
                )
            }
        }
        if (path.scope != Path.Scope.Providers)
            throw FileNotFoundException("invalid path: only providers supports sub-paths")
        return FileDocument(
            file = profileDir.resolve("providers")
                .resolve(path.relative.joinToString(separator = "/")),
            flags = setOf(Flag.Writable, Flag.Deletable),
        )
    }
}
