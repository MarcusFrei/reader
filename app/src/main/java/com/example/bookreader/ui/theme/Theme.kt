package com.example.bookreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bookreader.ui.reader.AppFonts

/** Палитра: пергамент, чернила, киноварь, позолота, кожа переплёта. */
object Medieval {
    val Parchment = Color(0xFFEADFC1)
    val ParchmentLight = Color(0xFFF3EAD2)
    val ParchmentDeep = Color(0xFFD8C79F)
    val Ink = Color(0xFF2B1D12)
    val Oxblood = Color(0xFF7A1F1B)
    val Gold = Color(0xFFA67C1F)
    val GoldLight = Color(0xFFD6A93E)
    val Leather = Color(0xFF3A2416)
    val Night = Color(0xFF17110B)
    val NightSurface = Color(0xFF241A11)
    val NightVariant = Color(0xFF33271B)
    val Candle = Color(0xFFE6D5B0)
}

private val ParchmentScheme = lightColorScheme(
    primary = Medieval.Oxblood,
    onPrimary = Medieval.ParchmentLight,
    primaryContainer = Color(0xFFE3C9A6),
    onPrimaryContainer = Medieval.Ink,
    secondary = Medieval.Gold,
    onSecondary = Color(0xFFFFF8E6),
    secondaryContainer = Color(0xFFE5D3A3),
    onSecondaryContainer = Medieval.Ink,
    tertiary = Color(0xFF3F5A3A),
    onTertiary = Medieval.ParchmentLight,
    background = Medieval.Parchment,
    onBackground = Medieval.Ink,
    surface = Color(0xFFEFE4C9),
    onSurface = Medieval.Ink,
    surfaceVariant = Color(0xFFDCCBA2),
    onSurfaceVariant = Color(0xFF5A4630),
    surfaceTint = Medieval.Oxblood,
    outline = Medieval.Gold,
    outlineVariant = Color(0xFFC2AC78),
    error = Color(0xFF9B1C1C),
    onError = Medieval.ParchmentLight,
    surfaceContainerLowest = Medieval.ParchmentLight,
    surfaceContainerLow = Color(0xFFEFE4C9),
    surfaceContainer = Color(0xFFE9DDBF),
    surfaceContainerHigh = Color(0xFFE2D4B1),
    surfaceContainerHighest = Color(0xFFDCCBA2)
)

private val NightScheme = darkColorScheme(
    primary = Medieval.GoldLight,
    onPrimary = Medieval.Night,
    primaryContainer = Color(0xFF4A3414),
    onPrimaryContainer = Color(0xFFF2DDB0),
    secondary = Color(0xFFC9A464),
    onSecondary = Medieval.Night,
    secondaryContainer = Color(0xFF3D2E18),
    onSecondaryContainer = Color(0xFFEAD7A8),
    tertiary = Color(0xFF8FB58A),
    onTertiary = Medieval.Night,
    background = Medieval.Night,
    onBackground = Medieval.Candle,
    surface = Medieval.NightSurface,
    onSurface = Medieval.Candle,
    surfaceVariant = Medieval.NightVariant,
    onSurfaceVariant = Color(0xFFC9B792),
    surfaceTint = Medieval.GoldLight,
    outline = Color(0xFF8A6A2A),
    outlineVariant = Color(0xFF5A4520),
    error = Color(0xFFE57373),
    onError = Medieval.Night,
    surfaceContainerLowest = Color(0xFF110C07),
    surfaceContainerLow = Color(0xFF1D150E),
    surfaceContainer = Medieval.NightSurface,
    surfaceContainerHigh = Color(0xFF2C2016),
    surfaceContainerHighest = Medieval.NightVariant
)

private val MedievalTypography = Typography(
    headlineSmall = TextStyle(fontFamily = AppFonts.Head, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = AppFonts.Head, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = AppFonts.Head, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = AppFonts.Head, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = AppFonts.Garamond, fontSize = 19.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = AppFonts.Garamond, fontSize = 17.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = AppFonts.Garamond, fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = AppFonts.Head, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AppFonts.Garamond, fontSize = 15.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = AppFonts.Garamond, fontSize = 13.sp, lineHeight = 18.sp)
)

private val MedievalShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) NightScheme else ParchmentScheme,
        typography = MedievalTypography,
        shapes = MedievalShapes,
        content = content
    )
}
