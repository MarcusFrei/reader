package com.example.bookreader.data

import android.net.Uri
import android.util.Base64
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

data class Block(val text: String, val heading: Boolean = false)
data class ChapterRef(val title: String, val blockIndex: Int)
class BookMeta(val title: String, val author: String?, val cover: ByteArray?)

class BookContent(val blocks: List<Block>, val chapters: List<ChapterRef>) {
    private val cumWords: IntArray by lazy {
        IntArray(blocks.size + 1).also { a ->
            for (i in blocks.indices) a[i + 1] = a[i] + blocks[i].text.count { it == ' ' } + 1
        }
    }

    fun remainingWords(index: Int): Int = cumWords[blocks.size] - cumWords[index.coerceIn(0, blocks.size)]
}

/** Фасад: формат -> метаданные и содержимое. */
object BookParser {
    fun meta(file: File, format: String, fallbackTitle: String): BookMeta = when (format) {
        "epub" -> EpubParser.meta(file, fallbackTitle)
        "fb2" -> Fb2Parser.meta(file, fallbackTitle)
        else -> BookMeta(fallbackTitle, null, null)
    }

    fun content(file: File, format: String, fallbackTitle: String = "Начало"): BookContent = when (format) {
        "epub" -> EpubParser.content(file)
        "fb2" -> Fb2Parser.content(file, fallbackTitle)
        else -> TxtParser.content(file, fallbackTitle)
    }
}

private val WS = Regex("\\s+")
private fun String.clean(): String = replace('\u00a0', ' ').replace(WS, " ").trim()

// ───────────────────────── EPUB ─────────────────────────
private object EpubParser {
    private class Item(val href: String, val type: String, val props: String)
    private class Opf(
        val title: String?,
        val author: String?,
        val manifest: Map<String, Item>,
        val spine: List<String>,
        val coverId: String?,
        val base: String
    )

    private fun ZipFile.text(path: String): String? {
        val e = getEntry(path) ?: return null
        return getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun normalize(path: String): String {
        val out = mutableListOf<String>()
        for (seg in path.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                else -> out.add(seg)
            }
        }
        return out.joinToString("/")
    }

    private fun resolve(base: String, href: String): String {
        val clean = Uri.decode(href.substringBefore('#'))
        return normalize(if (base.isEmpty()) clean else "$base/$clean")
    }

    private fun parseOpf(zip: ZipFile): Opf {
        val container = zip.text("META-INF/container.xml") ?: error("нет container.xml")
        val opfPath = Jsoup.parse(container, "", Parser.xmlParser())
            .selectFirst("rootfile")?.attr("full-path")?.takeIf { it.isNotBlank() }
            ?: error("нет OPF")
        val doc = Jsoup.parse(zip.text(opfPath) ?: error("нет OPF-файла"), "", Parser.xmlParser())
        val manifest = doc.select("manifest > item").associate {
            it.attr("id") to Item(it.attr("href"), it.attr("media-type"), it.attr("properties"))
        }
        val coverId = doc.select("meta[name=cover]").firstOrNull()?.attr("content")
            ?: manifest.entries.firstOrNull { "cover-image" in it.value.props }?.key
        return Opf(
            title = doc.getElementsByTag("dc:title").firstOrNull()?.text()?.trim(),
            author = doc.getElementsByTag("dc:creator").joinToString(", ") { it.text().trim() }.ifBlank { null },
            manifest = manifest,
            spine = doc.select("spine > itemref").map { it.attr("idref") },
            coverId = coverId,
            base = opfPath.substringBeforeLast('/', "")
        )
    }

    fun meta(file: File, fallback: String): BookMeta = ZipFile(file).use { zip ->
        val opf = parseOpf(zip)
        val cover = opf.coverId?.let { opf.manifest[it] }
            ?.takeIf { it.type.startsWith("image") }
            ?.let { item ->
                zip.getEntry(resolve(opf.base, item.href))?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
            }
        BookMeta(opf.title?.ifBlank { null } ?: fallback, opf.author, cover)
    }

    fun content(file: File): BookContent = ZipFile(file).use { zip ->
        val opf = parseOpf(zip)
        val blocks = ArrayList<Block>()
        val chapters = ArrayList<ChapterRef>()
        var n = 0
        for (id in opf.spine) {
            val item = opf.manifest[id] ?: continue
            if (!item.type.contains("html")) continue
            val html = zip.text(resolve(opf.base, item.href)) ?: continue
            val part = extractBlocks(Jsoup.parse(html).body())
            if (part.isEmpty()) continue
            n++
            chapters += ChapterRef(part.firstOrNull { it.heading }?.text ?: "Глава $n", blocks.size)
            blocks += part
        }
        BookContent(blocks, chapters)
    }

    private fun extractBlocks(body: Element): List<Block> {
        val out = ArrayList<Block>()
        for (el in body.select("h1,h2,h3,h4,h5,h6,p,li,blockquote,div,pre")) {
            val tag = el.tagName()
            val raw = when (tag) {
                "div", "blockquote" -> el.ownText()
                "li" -> if (el.selectFirst("p") != null) "" else el.text()
                else -> el.text()
            }
            val t = raw.clean()
            if (t.isEmpty()) continue
            out += Block(t, tag.length == 2 && tag[0] == 'h' && tag[1].isDigit())
        }
        return out
    }
}

// ───────────────────────── FB2 ─────────────────────────
private object Fb2Parser {
    private fun load(file: File): Document =
        file.inputStream().use { Jsoup.parse(it, null, "", Parser.xmlParser()) }

    fun meta(file: File, fallback: String): BookMeta {
        val d = load(file)
        val ti = d.selectFirst("description > title-info")
        val title = ti?.selectFirst("book-title")?.text()?.trim().orEmpty().ifBlank { fallback }
        val author = ti?.select("author")?.joinToString(", ") { a ->
            listOf("first-name", "middle-name", "last-name")
                .mapNotNull { tag -> a.selectFirst(tag)?.text()?.trim()?.takeIf { it.isNotEmpty() } }
                .joinToString(" ")
        }?.ifBlank { null }
        val href = ti?.selectFirst("coverpage image")?.attributes()
            ?.firstOrNull { it.key.endsWith("href") }?.value?.removePrefix("#")
        val cover = href?.let { id -> d.select("binary").firstOrNull { it.attr("id") == id } }
            ?.text()?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
        return BookMeta(title, author, cover)
    }

    fun content(file: File, fallbackTitle: String): BookContent {
        val d = load(file)
        val blocks = ArrayList<Block>()
        val chapters = ArrayList<ChapterRef>()
        for (body in d.select("body").filter { it.attr("name") != "notes" }) walk(body, blocks, chapters)
        if (chapters.firstOrNull()?.blockIndex != 0) chapters.add(0, ChapterRef(fallbackTitle, 0))
        return BookContent(blocks, chapters)
    }

    private fun walk(el: Element, blocks: MutableList<Block>, chapters: MutableList<ChapterRef>) {
        for (c in el.children()) {
            when (c.tagName()) {
                "section" -> {
                    c.children().firstOrNull { it.tagName() == "title" }?.text()?.clean()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { chapters += ChapterRef(it, blocks.size) }
                    walk(c, blocks, chapters)
                }
                "title", "subtitle" -> c.text().clean().takeIf { it.isNotEmpty() }?.let { blocks += Block(it, true) }
                "p", "v", "text-author" -> c.text().clean().takeIf { it.isNotEmpty() }?.let { blocks += Block(it) }
                "image", "empty-line" -> {}
                else -> walk(c, blocks, chapters)
            }
        }
    }
}

// ───────────────────────── TXT ─────────────────────────
private object TxtParser {
    private val chapterRe = Regex(
        "^(глава|часть|chapter|part|пролог|эпилог|prologue|epilogue)(\\s|\\.|:|$).*",
        RegexOption.IGNORE_CASE
    )

    fun content(file: File, fallbackTitle: String): BookContent {
        val text = decode(file.readBytes()).replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        val blank = lines.count { it.isBlank() }
        val paragraphs = if (blank > (lines.size - blank) * 0.3) {
            text.split(Regex("\n\\s*\n")).map { p -> p.lines().joinToString(" ") { it.trim() }.trim() }
        } else {
            lines.map { it.trim() }
        }
        val blocks = ArrayList<Block>()
        val chapters = ArrayList<ChapterRef>()
        for (p in paragraphs) {
            if (p.isEmpty()) continue
            val heading = p.length <= 80 && chapterRe.matches(p)
            if (heading) chapters += ChapterRef(p, blocks.size)
            blocks += Block(p, heading)
        }
        if (chapters.firstOrNull()?.blockIndex != 0) chapters.add(0, ChapterRef(fallbackTitle, 0))
        return BookContent(blocks, chapters)
    }

    /** UTF-8 / UTF-16 по BOM, иначе строгий UTF-8, иначе windows-1251 (частый случай для русских txt). */
    private fun decode(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte())
            return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            String(b, charset("windows-1251"))
        }
    }
}
