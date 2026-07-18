package com.github.yumelira.yumebox.runtime.service

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.github.yumelira.yumebox.runtime.service.document.Document
import com.github.yumelira.yumebox.runtime.service.document.FileDocument
import com.github.yumelira.yumebox.runtime.service.document.Flag
import com.github.yumelira.yumebox.runtime.service.document.Paths
import com.github.yumelira.yumebox.runtime.service.document.Picker
import java.io.FileNotFoundException
import android.provider.DocumentsContract.Document as D

class FilesProvider : DocumentsProvider() {
    companion object {
        private const val DEFAULT_ROOT_ID = "0"
        private val DEFAULT_DOCUMENT_COLUMNS = arrayOf(
            D.COLUMN_DOCUMENT_ID,
            D.COLUMN_DISPLAY_NAME,
            D.COLUMN_MIME_TYPE,
            D.COLUMN_LAST_MODIFIED,
            D.COLUMN_SIZE,
            D.COLUMN_FLAGS,
        )
        private val DEFAULT_ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
        )
        private val FLAG_VIRTUAL: Int =
            if (Build.VERSION.SDK_INT >= 24) D.FLAG_VIRTUAL_DOCUMENT else 0
    }
    private val picker: Picker by lazy {
        Picker(context!!)
    }
    override fun onCreate(): Boolean {
        return true
    }
    override fun queryRoots(projection: Array<out String>?): Cursor {
        val flags = Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD
        return MatrixCursor(projection ?: DEFAULT_ROOT_COLUMNS).apply {
            newRow().apply {
                add(Root.COLUMN_ROOT_ID, DEFAULT_ROOT_ID)
                add(Root.COLUMN_FLAGS, flags)
                add(Root.COLUMN_ICON, R.drawable.ic_logo_service)
                add(Root.COLUMN_TITLE, context!!.getString(R.string.files_provider_title))
                add(Root.COLUMN_SUMMARY, context!!.getString(R.string.files_provider_summary))
                add(Root.COLUMN_DOCUMENT_ID, "/")
                add(Root.COLUMN_MIME_TYPES, D.MIME_TYPE_DIR)
            }
        }
    }
    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        if (parentDocumentId == null || documentId == null)
            return false
        if (parentDocumentId == "/") return true
        return documentId.startsWith("$parentDocumentId/")
    }
    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val m = ParcelFileDescriptor.parseMode(mode)
        val path = Paths.resolve(documentId ?: "/")
        val document = picker.pick(path, mode?.requestWrite ?: false)
        require(document is FileDocument) {
            throw FileNotFoundException("invalid path $documentId")
        }
        return ParcelFileDescriptor.open(document.file, m)
    }
    override fun deleteDocument(documentId: String?) {
        val path = Paths.resolve(documentId ?: "/")
        if (path.relative == null)
            throw IllegalArgumentException("invalid path $documentId")
        val document = picker.pick(path, true)
        require(document is FileDocument) {
            throw FileNotFoundException("invalid path $documentId")
        }
        document.file.deleteRecursively()
        val authority = "${context!!.packageName}.files"
        context?.contentResolver?.notifyChange(
            android.provider.DocumentsContract.buildDocumentUri(authority, documentId),
            null
        )
    }
    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        return try {
            val doc = parentDocumentId ?: "/"
            val path = Paths.resolve(doc)
            val documents = picker.list(path)
            MatrixCursor(resolveDocumentProjection(projection)).apply {
                documents.forEach { document ->
                    newRow().applyDocument(document)
                        .add(D.COLUMN_DOCUMENT_ID, buildChildDocumentId(doc, document.id))
                }
            }
        } catch (e: Exception) {
            MatrixCursor(resolveDocumentProjection(projection))
        }
    }
    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        return try {
            val doc = documentId ?: "/"
            val path = Paths.resolve(doc)
            val document = picker.pick(path, false)
            MatrixCursor(resolveDocumentProjection(projection)).apply {
                newRow().applyDocument(document).add(D.COLUMN_DOCUMENT_ID, doc)
            }
        } catch (e: Exception) {
            MatrixCursor(resolveDocumentProjection(projection))
        }
    }
    private fun MatrixCursor.RowBuilder.applyDocument(document: Document): MatrixCursor.RowBuilder {
        var flags = 0
        document.flags.forEach {
            flags = when (it) {
                Flag.Writable -> flags or D.FLAG_SUPPORTS_WRITE
                Flag.Deletable -> flags or D.FLAG_SUPPORTS_DELETE
                Flag.Virtual -> flags or FLAG_VIRTUAL
            }
        }
        add(D.COLUMN_DISPLAY_NAME, document.name)
        add(D.COLUMN_MIME_TYPE, document.mimeType)
        add(D.COLUMN_LAST_MODIFIED, document.updatedAt)
        add(D.COLUMN_SIZE, document.size)
        add(D.COLUMN_FLAGS, flags)
        return this
    }
    private fun resolveDocumentProjection(projection: Array<out String>?): Array<out String> {
        return projection ?: DEFAULT_DOCUMENT_COLUMNS
    }
    private fun buildChildDocumentId(parentDocumentId: String, childId: String): String {
        return if (parentDocumentId == "/") "/$childId" else "$parentDocumentId/$childId"
    }
    private val String.requestWrite: Boolean
        get() = contains("w", ignoreCase = true)
}
