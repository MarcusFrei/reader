package com.example.bookreader.data

import com.github.junrar.Archive
import com.github.junrar.exception.UnsupportedRarV5Exception
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.File
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile

class Extracted(val name: String, val file: File)

/** Достаёт книги (EPUB / FB2 / TXT) из архивов ZIP, 7z и RAR (RAR4). */
object ArchiveExtractor {
    private val BOOK_EXT = setOf("epub", "fb2", "txt")
    private const val MAX_ENTRY = 300L * 1024 * 1024
    private const val MAX_7Z_IN_MEMORY = 150L * 1024 * 1024
    private const val MAX_BOOKS = 300

    /** Определяет тип архива по сигнатуре файла; null — это обычная книга. */
    fun kind(head: ByteArray, n: Int, ext: String, mime: String): String? {
        if (n >= 6 && head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() &&
            head[2] == 0xBC.toByte() && head[3] == 0xAF.toByte() &&
            head[4] == 0x27.toByte() && head[5] == 0x1C.toByte()
        ) return "7z"
        if (n >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "Rar!") return "rar"
        if (n >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            (ext == "zip" || (ext != "epub" && ext != "fb2" && mime.contains("zip")))
        ) return "zip"
        return null
    }

    /** ZIP с META-INF/container.xml — это на самом деле EPUB с «неправильным» расширением. */
    fun looksLikeEpub(file: File): Boolean = runCatching {
        ZipFile(file).use { it.getEntry("META-INF/container.xml") != null }
    }.getOrDefault(false)

    fun extract(kind: String, src: File, outDir: File): List<Extracted> = when (kind) {
        "zip" -> fromZip(src, outDir)
        "7z" -> from7z(src, outDir)
        "rar" -> fromRar(src, outDir)
        else -> emptyList()
    }

    // ── общее ──
    private fun baseName(raw: String) = raw.substringAfterLast('/').substringAfterLast('\\')

    private fun wanted(raw: String): Boolean {
        if (raw.contains("__MACOSX")) return false
        val n = baseName(raw)
        if (n.startsWith("._") || n.startsWith(".")) return false
        val low = n.lowercase()
        val ext = low.substringAfterLast('.', "")
        if (ext !in BOOK_EXT) return false
        if (ext == "txt" && (low.startsWith("readme") || low.startsWith("license") || low.startsWith("licence"))) return false
        return true
    }

    private fun target(outDir: File, index: Int, raw: String) = File(outDir, "${index}_${baseName(raw)}")

    private fun copyLimited(read: (ByteArray) -> Int, out: OutputStream) {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY) error("файл в архиве слишком большой")
            out.write(buf, 0, n)
        }
    }

    // ── ZIP ──
    private fun openZip(src: File): ZipFile =
        try {
            ZipFile(src)
        } catch (e: Exception) {
            // Старые русские архивы хранят имена в кодировке CP866
            ZipFile(src, Charset.forName("IBM866"))
        }

    private fun fromZip(src: File, outDir: File): List<Extracted> {
        val res = ArrayList<Extracted>()
        openZip(src).use { zip ->
            for (e in zip.entries().asSequence()) {
                if (e.isDirectory || !wanted(e.name)) continue
                if (res.size >= MAX_BOOKS) break
                val dest = target(outDir, res.size, e.name)
                try {
                    zip.getInputStream(e).use { input ->
                        dest.outputStream().use { out -> copyLimited({ input.read(it) }, out) }
                    }
                } catch (ex: Exception) {
                    dest.delete()
                    throw IllegalStateException("${baseName(e.name)}: ${ex.message ?: "ошибка чтения (возможно, пароль)"}")
                }
                res.add(Extracted(baseName(e.name), dest))
            }
        }
        return res
    }

    // ── 7z ──
    private fun from7z(src: File, outDir: File): List<Extracted> {
        if (src.length() > MAX_7Z_IN_MEMORY) error("7z-архив слишком большой (больше 150 МБ)")
        val res = ArrayList<Extracted>()
        try {
            SevenZFile(SeekableInMemoryByteChannel(src.readBytes())).use { sz ->
                while (true) {
                    val e = sz.nextEntry ?: break
                    val name = e.name ?: ""
                    if (!e.isDirectory && wanted(name) && res.size < MAX_BOOKS) {
                        val dest = target(outDir, res.size, name)
                        dest.outputStream().use { out -> copyLimited({ sz.read(it) }, out) }
                        res.add(Extracted(baseName(name), dest))
                    }
                }
            }
        } catch (ex: OutOfMemoryError) {
            error("не хватило памяти для распаковки 7z")
        } catch (ex: Exception) {
            if (ex.javaClass.simpleName.contains("Password")) error("7z-архив защищён паролем")
            throw ex
        }
        return res
    }

    // ── RAR (RAR4; RAR5 библиотека не поддерживает) ──
    private fun fromRar(src: File, outDir: File): List<Extracted> {
        val res = ArrayList<Extracted>()
        val archive = try {
            Archive(src)
        } catch (ex: UnsupportedRarV5Exception) {
            error("формат RAR5 не поддерживается — пересохраните архив как ZIP, 7z или RAR4")
        }
        archive.use { a ->
            if (a.isEncrypted) error("RAR-архив защищён паролем")
            while (true) {
                val h = a.nextFileHeader() ?: break
                val name = h.fileName ?: ""
                if (!h.isDirectory && wanted(name) && res.size < MAX_BOOKS) {
                    if (h.isEncrypted) error("RAR-архив защищён паролем")
                    val dest = target(outDir, res.size, name)
                    dest.outputStream().use { out -> a.extractFile(h, out) }
                    res.add(Extracted(baseName(name), dest))
                }
            }
        }
        return res
    }
}
