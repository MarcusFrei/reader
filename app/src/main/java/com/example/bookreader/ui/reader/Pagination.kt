package com.example.bookreader.ui.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.util.LruCache
import com.example.bookreader.data.BookContent
import com.example.bookreader.data.settings.FontFamilyOpt
import kotlinx.coroutines.ensureActive
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** Геометрия и параметры шрифта. Страницы пересчитываются только при её изменении. */
data class Geometry(
    val widthPx: Int,
    val heightPx: Int,
    val textSizePx: Float,
    val lineMult: Float,
    val font: FontFamilyOpt,
    val bold: Boolean,
    val justify: Boolean,
    val dropCap: Boolean,
    val lang: String
)

class PageItem(
    val block: Int,
    val startLine: Int,
    val endLine: Int,
    val y: Float,
    val charStart: Int,   // смещение первого символа куска в тексте блока
    val heading: Boolean
)

class Page(val items: List<PageItem>) {
    val firstBlock: Int get() = items[0].block
    val firstChar: Int get() = items[0].charStart
}

class Built(val layout: StaticLayout, val shift: Int)

/** Буквица: крупная первая буква абзаца, занимающая две строки. */
class DropCapSpan(
    private val letter: String,
    private val paint: TextPaint,
    private val gap: Int
) : LeadingMarginSpan.LeadingMarginSpan2 {
    private val w = paint.measureText(letter).toInt() + gap

    override fun getLeadingMarginLineCount(): Int = 2
    override fun getLeadingMargin(first: Boolean): Int = if (first) w else 0

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout
    ) {
        if (!first) return
        val y = if (layout.lineCount > 1) layout.getLineBaseline(1) else baseline
        c.drawText(letter, x.toFloat(), y.toFloat(), paint)
    }
}

class LayoutFactory(
    private val content: BookContent,
    private val g: Geometry,
    private val fonts: FontSet,
    private val fg: Int,
    private val accent: Int
) {
    private val locale = Locale.forLanguageTag(g.lang)
    private val bodyFace = fonts.body(g.font, g.bold)

    fun create(i: Int): Built {
        val b = content.blocks[i]
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textLocale = locale
        paint.color = if (b.heading) accent else fg

        if (b.heading) {
            paint.typeface = fonts.heading
            paint.textSize = g.textSizePx * 1.3f
            val l = StaticLayout.Builder.obtain(b.text, 0, b.text.length, paint, g.widthPx)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
            return Built(l, 0)
        }

        paint.typeface = bodyFace
        paint.textSize = g.textSizePx
        val first = i > 0 && content.blocks[i - 1].heading
        val text = b.text
        val sp: SpannableString
        var shift = 0
        if (g.dropCap && first && text.length > 80 && text[0].isLetter()) {
            val dropPaint = TextPaint(paint)
            dropPaint.typeface = fonts.heading
            dropPaint.textSize = g.textSizePx * (1.2f * g.lineMult + 0.7f) / 0.7f
            dropPaint.color = accent
            sp = SpannableString(text.substring(1))
            shift = 1
            sp.setSpan(
                DropCapSpan(text.substring(0, 1), dropPaint, (g.textSizePx * 0.18f).toInt()),
                0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else {
            sp = SpannableString(text)
            if (!first) {
                sp.setSpan(
                    LeadingMarginSpan.Standard((g.textSizePx * 1.4f).toInt(), 0),
                    0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        val builder = StaticLayout.Builder.obtain(sp, 0, sp.length, paint, g.widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, g.lineMult)
            .setIncludePad(false)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        if (g.justify && Build.VERSION.SDK_INT >= 26) {
            builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        return Built(builder.build(), shift)
    }
}

/** Кэш раскладок для отрисовки (страницы хранят только описание, раскладка строится заново). */
class LayoutCache(private val factory: LayoutFactory) {
    private val lru = LruCache<Int, Built>(48)

    fun get(i: Int): Built {
        val hit = lru.get(i)
        if (hit != null) return hit
        val built = factory.create(i)
        lru.put(i, built)
        return built
    }
}

fun detectLang(c: BookContent): String {
    var cyr = 0
    var lat = 0
    for (b in c.blocks.take(40)) {
        for (ch in b.text.take(200)) {
            if (ch in '\u0400'..'\u04FF') cyr++ else if (ch.isLetter()) lat++
        }
    }
    return if (cyr > lat) "ru" else "en"
}

/**
 * Разбивает книгу на страницы. Строки не режутся пополам; в начале/конце страницы не остаётся
 * одиночных строк абзаца (вдовы и сироты); заголовок не остаётся в одиночестве внизу страницы;
 * новая глава начинается с новой страницы.
 */
suspend fun paginate(
    content: BookContent,
    g: Geometry,
    fonts: FontSet,
    onProgress: (Float) -> Unit
): List<Page> {
    val factory = LayoutFactory(content, g, fonts, 0, 0)
    val chapterStarts = content.chapters.map { it.blockIndex }.toHashSet()
    val pageH = g.heightPx.toFloat()
    val headTop = g.textSizePx * 1.4f
    val headBottom = g.textSizePx * 1.8f
    val lineGuess = g.textSizePx * 1.2f * g.lineMult
    val blocks = content.blocks

    val pages = ArrayList<Page>()
    var items = ArrayList<PageItem>()
    var y = 0f

    fun flush() {
        if (items.isNotEmpty()) {
            pages.add(Page(items))
            items = ArrayList()
        }
        y = 0f
    }

    for (i in blocks.indices) {
        if ((i and 63) == 0) {
            coroutineContext.ensureActive()
            onProgress(i.toFloat() / blocks.size)
        }
        val built = factory.create(i)
        val l = built.layout

        if (blocks[i].heading) {
            val h = l.height.toFloat()
            if (y > 0f && chapterStarts.contains(i)) flush()
            var top = if (y > 0f) headTop else 0f
            if (y > 0f && y + top + h + headBottom + lineGuess * 2 > pageH) {
                flush()
                top = 0f
            }
            items.add(PageItem(i, 0, l.lineCount, y + top, 0, true))
            y += top + h + headBottom
            continue
        }

        val n = l.lineCount
        var line = 0
        while (line < n) {
            val remaining = n - line
            val base = l.getLineTop(line)
            var fit = 0
            while (fit < remaining && y + (l.getLineBottom(line + fit) - base) <= pageH) fit++

            if (fit >= remaining) {
                items.add(PageItem(i, line, n, y, l.getLineStart(line) + built.shift, false))
                y += (l.getLineBottom(n - 1) - base).toFloat()
                line = n
            } else {
                val minTake = if (line == 0) 2 else 1
                var take = fit
                if (take < minTake) {
                    take = 0
                } else if (n - (line + take) == 1 && take - 1 >= minTake) {
                    take -= 1
                }
                if (take == 0) {
                    if (items.isEmpty()) {
                        take = maxOf(fit, 1)
                    } else {
                        flush()
                        continue
                    }
                }
                items.add(PageItem(i, line, line + take, y, l.getLineStart(line) + built.shift, false))
                line += take
                flush()
            }
        }
    }
    flush()
    return pages
}

/** Номер страницы, содержащей место (блок, символ). */
fun findPage(pages: List<Page>, block: Int, ch: Int): Int {
    var lo = 0
    var hi = pages.size - 1
    var ans = 0
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val p = pages[mid]
        val cmp = if (p.firstBlock != block) p.firstBlock.compareTo(block) else p.firstChar.compareTo(ch)
        if (cmp <= 0) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return ans
}

/** Рисует текст страницы. Каждый кусок абзаца отсекается по границам строк. */
fun drawPageText(c: Canvas, page: Page, cache: LayoutCache, g: Geometry, accent: Int) {
    for (it in page.items) {
        val l = cache.get(it.block).layout
        val top = l.getLineTop(it.startLine)
        val bottom = l.getLineBottom(it.endLine - 1)
        c.save()
        c.translate(0f, it.y)
        c.clipRect(0f, -g.textSizePx * 0.15f, g.widthPx.toFloat(), (bottom - top).toFloat())
        c.translate(0f, -top.toFloat())
        l.draw(c)
        c.restore()
        if (it.heading) {
            drawOrnament(c, g.widthPx, it.y + (bottom - top) + g.textSizePx * 0.9f, g.textSizePx, accent)
        }
    }
}

/** Орнамент под заголовком главы: линия, ромб, линия. */
private fun drawOrnament(c: Canvas, w: Int, y: Float, size: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = color
    p.alpha = 190
    p.strokeWidth = maxOf(1f, size * 0.05f)
    p.style = Paint.Style.STROKE
    val cx = w / 2f
    val gap = size * 0.55f
    val len = w * 0.22f
    c.drawLine(cx - gap - len, y, cx - gap, y, p)
    c.drawLine(cx + gap, y, cx + gap + len, y, p)
    val r = size * 0.28f
    val path = Path()
    path.moveTo(cx, y - r)
    path.lineTo(cx + r, y)
    path.lineTo(cx, y + r)
    path.lineTo(cx - r, y)
    path.close()
    p.style = Paint.Style.FILL
    c.drawPath(path, p)
}
