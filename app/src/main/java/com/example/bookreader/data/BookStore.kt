package com.example.bookreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Импорт и удаление книг: копируем файл во внутреннее хранилище приложения. */
class BookStore(private val ctx: Context, private val dao: BookDao) {

    private val booksDir get() = File(ctx.filesDir, "books").apply { mkdirs() }
    private val coversDir get() = File(ctx.filesDir, "covers").apply { mkdirs() }

    suspend fun importAll(uris: List<Uri>): String = withContext(Dispatchers.IO) {
        var ok = 0
        val errors = mutableListOf<String>()
        for (u in uris) {
            runCatching { importOne(u) }
                .onSuccess { ok++ }
                .onFailure { errors += it.message ?: "ошибка" }
        }
        buildString {
            if (ok > 0) append("Добавлено книг: $ok")
            if (errors.isNotEmpty()) {
                if (isNotEmpty()) append(". ")
                append("Не удалось: ").append(errors.joinToString("; "))
            }
        }
    }

    private suspend fun importOne(uri: Uri) {
        val resolver = ctx.contentResolver
        val name = displayName(uri) ?: "book"
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri).orEmpty().lowercase()
        val format = when {
            ext == "epub" || mime.contains("epub") -> "epub"
            ext == "fb2" || mime.contains("fictionbook") -> "fb2"
            ext == "txt" || mime == "text/plain" -> "txt"
            ext == "pdf" || mime.contains("pdf") -> error("PDF пока не поддерживается ($name)")
            else -> error("неподдерживаемый формат ($name)")
        }
        val fallbackTitle = name.substringBeforeLast('.')
        val dest = File(booksDir, "${UUID.randomUUID()}.$format")
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { out -> input.copyTo(out) }
        } ?: error("не удалось открыть файл ($name)")

        try {
            val meta = BookParser.meta(dest, format, fallbackTitle)
            var coverPath: String? = null
            meta.cover?.let { bytes ->
                val cf = File(coversDir, dest.nameWithoutExtension + ".jpg")
                if (saveCover(bytes, cf)) coverPath = cf.absolutePath
            }
            dao.insert(
                BookEntity(
                    title = meta.title.ifBlank { fallbackTitle },
                    author = meta.author,
                    format = format,
                    filePath = dest.absolutePath,
                    coverPath = coverPath,
                    addedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            dest.delete()
            throw IllegalStateException("$name: ${e.message ?: "повреждённый файл"}")
        }
    }

    suspend fun delete(book: BookEntity) = withContext(Dispatchers.IO) {
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
        dao.delete(book)
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment

    private fun saveCover(bytes: ByteArray, out: File): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outHeight / sample > 1200) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        )
        if (bmp == null) {
            false
        } else {
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            true
        }
    }.getOrDefault(false)
}
