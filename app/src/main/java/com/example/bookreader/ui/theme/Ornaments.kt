package com.example.bookreader.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Линия с затуханием к краям и ромбом посередине. */
@Composable
fun OrnamentDivider(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outline) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val cy = size.height / 2f
        val cx = size.width / 2f
        val gap = 12.dp.toPx()
        val sw = 1.dp.toPx()
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, color), startX = 0f, endX = cx - gap),
            start = Offset(0f, cy), end = Offset(cx - gap, cy), strokeWidth = sw
        )
        drawLine(
            brush = Brush.horizontalGradient(listOf(color, Color.Transparent), startX = cx + gap, endX = size.width),
            start = Offset(cx + gap, cy), end = Offset(size.width, cy), strokeWidth = sw
        )
        val r = 4.5.dp.toPx()
        val diamond = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r, cy)
            close()
        }
        drawPath(diamond, color)
        val dot = 1.6.dp.toPx()
        drawCircle(color, dot, Offset(cx - gap + 2.dp.toPx(), cy))
        drawCircle(color, dot, Offset(cx + gap - 2.dp.toPx(), cy))
    }
}

/** Фон «старая бумага»: однотонный пергамент, темнеющий к краям. */
@Composable
fun ParchmentBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x33000000)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = max(size.width, size.height) * 0.85f
                    )
                )
            },
        content = content
    )
}
