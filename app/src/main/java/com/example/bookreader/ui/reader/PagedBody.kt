package com.example.bookreader.ui.reader

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bookreader.data.BookContent
import com.example.bookreader.data.settings.AlignOpt
import com.example.bookreader.data.settings.PageAnim
import com.example.bookreader.data.settings.ReaderColors
import com.example.bookreader.data.settings.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private data class Spec(val geom: Geometry, val left: Int, val top: Int)
private class Pag(val spec: Spec, val pages: List<Page>)

private class PageEnv(
    val pag: Pag,
    val cache: LayoutCache,
    val content: BookContent,
    val paper: Color,
    val ink: Color,
    val accent: Color,
    val accentArgb: Int,
    val frame: Boolean
)

/**
 * Книжное чтение: текст разбит на страницы, страницы листаются свайпом, тапом или кнопками.
 * Вёрстка идёт в фоне; при смене шрифта/полей место чтения сохраняется.
 */
@Composable
fun PagedBody(
    content: BookContent,
    s: ReaderSettings,
    colors: ReaderColors,
    fontSp: Float,
    fonts: FontSet,
    state: BodyState
) {
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lang = remember(content) { detectLang(content) }

    var pag by remember { mutableStateOf<Pag?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(true) }
    var current by remember { mutableIntStateOf(0) }
    var turnDir by remember { mutableIntStateOf(0) }
    var turnP by remember { mutableFloatStateOf(0f) }
    var animating by remember { mutableStateOf(false) }
    var anchorBlock by remember { mutableIntStateOf(state.block) }
    var anchorChar by remember { mutableIntStateOf(state.char) }

    // ── команды оболочке ──
    val turn: (Int) -> Unit = { dir ->
        val total = pag?.pages?.size ?: 0
        val t = current + dir
        if (!animating && t in 0 until total) {
            if (s.pageAnim == PageAnim.NONE) {
                current = t
            } else {
                scope.launch {
                    animating = true
                    turnDir = dir
                    turnP = 0f
                    animate(0f, 1f, animationSpec = tween(320)) { v, _ -> turnP = v }
                    current = t
                    turnDir = 0
                    turnP = 0f
                    animating = false
                }
            }
        }
    }
    SideEffect {
        state.turn = turn
        state.jumpBlock = { b ->
            val pg = pag
            if (pg != null) current = findPage(pg.pages, b, 0)
        }
        state.jumpFraction = { f ->
            val pg = pag
            if (pg != null && pg.pages.isNotEmpty()) {
                current = (f * (pg.pages.size - 1)).roundToInt().coerceIn(0, pg.pages.size - 1)
            }
        }
    }

    // ── публикуем позицию ──
    LaunchedEffect(current, pag) {
        val pg = pag
        val page = pg?.pages?.getOrNull(current)
        if (pg != null && page != null) {
            anchorBlock = page.firstBlock
            anchorChar = page.firstChar
            state.block = page.firstBlock
            state.char = page.firstChar
            state.fraction = if (pg.pages.size <= 1) 0f else current.toFloat() / (pg.pages.size - 1)
            state.pageText = "${current + 1} / ${pg.pages.size}"
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight

        val spec = with(localDensity) {
            val mL = s.marginLeft.dp.toPx()
            val mR = s.marginRight.dp.toPx()
            val mT = s.marginTop.dp.toPx()
            val mB = s.marginBottom.dp.toPx()
            val headerH = 30.dp.toPx()
            val footerH = 34.dp.toPx()
            val areaW = (wPx - mL - mR).coerceAtLeast(120f)
            val textW = (areaW * s.width.fraction).toInt().coerceAtLeast(100)
            val textH = (hPx - headerH - footerH - mT - mB).toInt().coerceAtLeast(200)
            Spec(
                Geometry(
                    widthPx = textW,
                    heightPx = textH,
                    textSizePx = fontSp.sp.toPx(),
                    lineMult = s.lineSpacing,
                    font = s.fontFamily,
                    bold = s.bold,
                    justify = s.align == AlignOpt.JUSTIFY,
                    dropCap = s.dropCap,
                    lang = lang
                ),
                left = (mL + (areaW - textW) / 2f).toInt(),
                top = (headerH + mT).toInt()
            )
        }

        // Пересчёт страниц в фоне при любом изменении геометрии
        LaunchedEffect(spec) {
            if (pag != null) delay(250)
            busy = true
            val ab = anchorBlock
            val ac = anchorChar
            val result = withContext(Dispatchers.Default) {
                paginate(content, spec.geom, fonts) { progress = it }
            }
            pag = Pag(spec, result)
            current = findPage(result, ab, ac)
            busy = false
        }

        val p = pag
        if (p != null && p.pages.isNotEmpty()) {
            val pages = p.pages
            val fg = colors.fg.toArgb()
            val ac = colors.accent.toArgb()
            val cache = remember(p, fg, ac) {
                LayoutCache(LayoutFactory(content, p.spec.geom, fonts, fg, ac))
            }
            val env = remember(p, cache, colors.bg, colors.fg, colors.accent, s.pageFrame) {
                PageEnv(p, cache, content, colors.bg, colors.fg, colors.accent, ac, s.pageFrame)
            }

            // Заранее строим раскладки соседних страниц, чтобы листание не тормозило
            LaunchedEffect(current, cache) {
                withContext(Dispatchers.Default) {
                    for (d in -1..2) {
                        pages.getOrNull(current + d)?.items?.forEach { cache.get(it.block) }
                    }
                }
            }

            val leaveNow: () -> Float = { if (turnDir > 0) turnP else 1f - turnP }
            val pageCount = pages.size
            val under = if (turnDir > 0) current + 1 else current
            val topIdx = when {
                turnDir > 0 -> current
                turnDir < 0 -> current - 1
                else -> -1
            }

            Box(
                Modifier.fillMaxSize().pointerInput(pageCount, s.pageAnim) {
                    var acc = 0f
                    val settle: suspend (Float) -> Unit = { a ->
                        if (turnDir == 0) {
                            if (s.pageAnim == PageAnim.NONE && abs(a) > 0.15f) {
                                val t = current + (if (a > 0f) 1 else -1)
                                if (t in 0 until pageCount) current = t
                            }
                        } else {
                            val complete = abs(a) > 0.22f
                            animating = true
                            animate(turnP, if (complete) 1f else 0f, animationSpec = tween(200)) { v, _ -> turnP = v }
                            if (complete) current += turnDir
                            turnDir = 0
                            turnP = 0f
                            animating = false
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { scope.launch { settle(acc) } },
                        onDragCancel = { scope.launch { settle(0f) } },
                        onHorizontalDrag = { change, dx ->
                            if (!animating) {
                                change.consume()
                                acc = (acc - dx / size.width).coerceIn(-1f, 1f)
                                if (s.pageAnim != PageAnim.NONE) {
                                    val d = if (acc > 0f) 1 else if (acc < 0f) -1 else 0
                                    val t = current + d
                                    if (d != 0 && t in 0 until pageCount) {
                                        turnDir = d
                                        turnP = abs(acc)
                                    } else {
                                        turnDir = 0
                                        turnP = 0f
                                    }
                                }
                            }
                        }
                    )
                }
            ) {
                PageView(under.coerceIn(0, pageCount - 1), env)

                if (topIdx in 0 until pageCount) {
                    // Тень, ложащаяся на нижнюю страницу
                    Box(Modifier.fillMaxSize().drawBehind {
                        drawRect(Color.Black.copy(alpha = ((1f - leaveNow()) * 0.28f).coerceIn(0f, 1f)))
                    })
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            val leave = leaveNow()
                            if (s.pageAnim == PageAnim.FLIP) {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = -leave * 90f
                                cameraDistance = 14f * density
                                alpha = if (leave >= 0.985f) 0f else 1f
                            } else {
                                translationX = -leave * size.width
                            }
                        }
                    ) {
                        PageView(topIdx, env)
                        Box(Modifier.fillMaxSize().drawBehind {
                            val leave = leaveNow()
                            if (s.pageAnim == PageAnim.FLIP) {
                                drawRect(Color.Black.copy(alpha = (leave * 0.35f).coerceIn(0f, 1f)))
                            } else {
                                val edge = 26.dp.toPx()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color(0x55000000)),
                                        startX = size.width - edge,
                                        endX = size.width
                                    ),
                                    topLeft = Offset(size.width - edge, 0f),
                                    size = Size(edge, size.height)
                                )
                            }
                        })
                    }
                }
            }
        } else if (p == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
        }

        if (busy) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                color = Color(0xCC000000)
            ) {
                Text(
                    "Вёрстка страниц… ${(progress * 100).roundToInt()}%",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PageView(index: Int, env: PageEnv) {
    val page = env.pag.pages[index]
    val g = env.pag.spec.geom
    val chapter = remember(index, env) {
        env.content.chapters.lastOrNull { it.blockIndex <= page.firstBlock }?.title.orEmpty()
    }
    Box(Modifier.fillMaxSize().background(env.paper)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Старая бумага: потемнение к краям
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x2A000000)),
                    center = Offset(w / 2f, h / 2f),
                    radius = max(w, h) * 0.78f
                )
            )
            if (env.frame) drawFrame(env.accent)
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.translate(env.pag.spec.left.toFloat(), env.pag.spec.top.toFloat())
                drawPageText(nc, page, env.cache, g, env.accentArgb)
                nc.restore()
            }
            drawFooterOrnament(env.accent)
        }
        Text(
            chapter,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp, start = 36.dp, end = 36.dp),
            color = env.accent.copy(alpha = 0.85f),
            fontFamily = AppFonts.Head,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${index + 1}",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            color = env.ink.copy(alpha = 0.8f),
            fontFamily = AppFonts.Head,
            fontSize = 15.sp
        )
    }
}

/** Двойная рамка страницы с квадратиками по углам. */
private fun DrawScope.drawFrame(color: Color) {
    val w = size.width
    val h = size.height
    val a = 8.dp.toPx()
    drawRect(
        color = color.copy(alpha = 0.6f),
        topLeft = Offset(a, a),
        size = Size(w - 2 * a, h - 2 * a),
        style = Stroke(1.3.dp.toPx())
    )
    val b = a + 4.dp.toPx()
    drawRect(
        color = color.copy(alpha = 0.35f),
        topLeft = Offset(b, b),
        size = Size(w - 2 * b, h - 2 * b),
        style = Stroke(0.7.dp.toPx())
    )
    val q = 4.dp.toPx()
    val sq = Size(q, q)
    val c = color.copy(alpha = 0.7f)
    drawRect(c, Offset(a - q / 2, a - q / 2), sq)
    drawRect(c, Offset(w - a - q / 2, a - q / 2), sq)
    drawRect(c, Offset(a - q / 2, h - a - q / 2), sq)
    drawRect(c, Offset(w - a - q / 2, h - a - q / 2), sq)
}

/** Линии с ромбами по бокам от номера страницы. */
private fun DrawScope.drawFooterOrnament(color: Color) {
    val cx = size.width / 2f
    val y = size.height - 21.dp.toPx()
    val gap = 24.dp.toPx()
    val len = 36.dp.toPx()
    val c = color.copy(alpha = 0.55f)
    val sw = 1.dp.toPx()
    drawLine(c, Offset(cx - gap - len, y), Offset(cx - gap, y), strokeWidth = sw)
    drawLine(c, Offset(cx + gap, y), Offset(cx + gap + len, y), strokeWidth = sw)
    val d = 2.5.dp.toPx()
    drawRect(c, Offset(cx - gap - len - d, y - d), Size(2 * d, 2 * d))
    drawRect(c, Offset(cx + gap + len - d, y - d), Size(2 * d, 2 * d))
}
