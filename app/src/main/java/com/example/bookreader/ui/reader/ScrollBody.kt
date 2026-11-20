package com.example.bookreader.ui.reader

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bookreader.data.Block
import com.example.bookreader.data.BookContent
import com.example.bookreader.data.settings.AlignOpt
import com.example.bookreader.data.settings.PageAnim
import com.example.bookreader.data.settings.ReaderColors
import com.example.bookreader.data.settings.ReaderSettings
import kotlinx.coroutines.launch

/** Режим «лента»: непрерывная прокрутка (запасной вариант, основной режим — страницы). */
@Composable
fun ScrollBody(
    content: BookContent,
    s: ReaderSettings,
    colors: ReaderColors,
    fontSp: Float,
    state: BodyState
) {
    val total = content.blocks.size
    val last = (total - 1).coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.block.coerceIn(0, last))
    val family = AppFonts.compose(s.fontFamily)

    SideEffect {
        state.turn = { dir ->
            scope.launch {
                val info = listState.layoutInfo
                val h = (info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding) * 0.92f
                if (s.pageAnim == PageAnim.NONE) listState.scrollBy(dir * h)
                else listState.animateScrollBy(dir * h, tween(260))
            }
        }
        state.jumpBlock = { b -> scope.launch { listState.scrollToItem(b.coerceIn(0, last)) } }
        state.jumpFraction = { f -> scope.launch { listState.scrollToItem((f * last).toInt().coerceIn(0, last)) } }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { i ->
            state.block = i
            state.char = 0
            state.fraction = if (total <= 1) 0f else i.toFloat() / (total - 1)
            state.pageText = ""
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = s.marginTop.dp + 16.dp, bottom = s.marginBottom.dp + 16.dp),
        modifier = Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        itemsIndexed(content.blocks) { _, b -> BlockView(b, s, fontSp, colors, family) }
    }
}

@Composable
private fun BlockView(b: Block, s: ReaderSettings, fontSp: Float, colors: ReaderColors, family: FontFamily) {
    val base = TextStyle(
        fontSize = fontSp.sp,
        lineHeight = (fontSp * s.lineSpacing * 1.15f).sp,
        fontFamily = family,
        fontWeight = if (s.bold) FontWeight.Bold else FontWeight.Normal,
        color = colors.fg,
        textAlign = if (s.align == AlignOpt.JUSTIFY) TextAlign.Justify else TextAlign.Start,
        hyphens = Hyphens.Auto,
        lineBreak = LineBreak.Paragraph
    )
    Box(Modifier.fillMaxWidth().padding(start = s.marginLeft.dp, end = s.marginRight.dp)) {
        if (b.heading) {
            Text(
                b.text,
                modifier = Modifier.fillMaxWidth(s.width.fraction).align(Alignment.Center)
                    .padding(top = fontSp.dp, bottom = (fontSp * 0.7f).dp),
                style = base.copy(
                    fontSize = (fontSp * 1.3f).sp,
                    lineHeight = (fontSp * 1.3f * s.lineSpacing).sp,
                    fontFamily = AppFonts.Head,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    textAlign = TextAlign.Center
                )
            )
        } else {
            Text(
                b.text,
                modifier = Modifier.fillMaxWidth(s.width.fraction).align(Alignment.Center)
                    .padding(bottom = (fontSp * 0.45f).dp),
                style = base.copy(textIndent = TextIndent(firstLine = (fontSp * 1.4f).sp))
            )
        }
    }
}
