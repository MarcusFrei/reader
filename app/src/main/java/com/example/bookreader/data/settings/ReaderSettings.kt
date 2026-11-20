package com.example.bookreader.data.settings

import androidx.compose.ui.graphics.Color
import com.example.bookreader.domain.Gesture
import com.example.bookreader.domain.ReaderAction
import com.example.bookreader.domain.Zone
import com.example.bookreader.domain.defaultGestures
import com.example.bookreader.domain.gestureKey
import org.json.JSONObject

enum class FontFamilyOpt(val label: String) {
    GARAMOND("EB Garamond"),
    OLD_STANDARD("Old Standard"),
    ALEGREYA("Alegreya"),
    SERIF("Системный с засечками"),
    SANS("Без засечек"),
    MONO("Моноширинный")
}
enum class WidthOpt(val label: String, val fraction: Float) {
    NARROW("Узкая", 0.72f), STANDARD("Стандартная", 0.88f), WIDE("Широкая", 1f)
}
enum class AlignOpt(val label: String) { LEFT("По левому краю"), JUSTIFY("По ширине") }
enum class ThemeOpt(val label: String) {
    PARCHMENT("Пергамент"), SEPIA("Сепия"), LIGHT("Светлая"), DARK("Ночная свеча"), CUSTOM("Своя")
}
enum class OrientationOpt(val label: String) { AUTO("Авто"), PORTRAIT("Портрет"), LANDSCAPE("Альбом") }
enum class ScrollMode(val label: String) { PAGED("Книжные страницы"), SCROLL("Лента") }
enum class PageAnim(val label: String) { NONE("Без анимации"), SLIDE("Сдвиг"), FLIP("Перелистывание") }

class ReaderColors(val bg: Color, val fg: Color, val accent: Color)

data class ReaderSettings(
    val fontSize: Float = 20f,
    val fontFamily: FontFamilyOpt = FontFamilyOpt.GARAMOND,
    val bold: Boolean = false,
    val lineSpacing: Float = 1.25f,
    val width: WidthOpt = WidthOpt.WIDE,
    val marginLeft: Int = 20,
    val marginRight: Int = 20,
    val marginTop: Int = 8,
    val marginBottom: Int = 8,
    val align: AlignOpt = AlignOpt.JUSTIFY,
    val theme: ThemeOpt = ThemeOpt.PARCHMENT,
    val customBg: Int = 0xFF1B1B1F.toInt(),
    val customFg: Int = 0xFFE0E0E0.toInt(),
    val brightness: Float = -1f,               // -1 = системная
    val orientation: OrientationOpt = OrientationOpt.AUTO,
    val scrollMode: ScrollMode = ScrollMode.PAGED,
    val pageAnim: PageAnim = PageAnim.FLIP,
    val dropCap: Boolean = true,
    val pageFrame: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoNight: Boolean = false,
    val nightStartMin: Int = 22 * 60,
    val nightEndMin: Int = 7 * 60,
    val exitGuard: Boolean = true,
    val gestures: Map<String, String> = defaultGestures()
) {
    fun actionFor(zone: Zone, gesture: Gesture): ReaderAction =
        gestures[gestureKey(zone, gesture)]
            ?.let { runCatching { ReaderAction.valueOf(it) }.getOrNull() }
            ?: ReaderAction.NONE

    fun colors(t: ThemeOpt = theme): ReaderColors = when (t) {
        ThemeOpt.PARCHMENT -> ReaderColors(Color(0xFFEADFC1), Color(0xFF2B1D12), Color(0xFF7A1F1B))
        ThemeOpt.SEPIA -> ReaderColors(Color(0xFFF4ECD8), Color(0xFF5B4636), Color(0xFF8A4B2A))
        ThemeOpt.LIGHT -> ReaderColors(Color(0xFFFFFFFF), Color(0xFF1C1C1C), Color(0xFF7A1F1B))
        ThemeOpt.DARK -> ReaderColors(Color(0xFF17110B), Color(0xFFD9C7A0), Color(0xFFC9983A))
        ThemeOpt.CUSTOM -> ReaderColors(Color(customBg), Color(customFg), Color(customFg))
    }

    fun toJson(): String = JSONObject().apply {
        put("fontSize", fontSize.toDouble())
        put("fontFamily", fontFamily.name)
        put("bold", bold)
        put("lineSpacing", lineSpacing.toDouble())
        put("width", width.name)
        put("mL", marginLeft); put("mR", marginRight); put("mT", marginTop); put("mB", marginBottom)
        put("align", align.name)
        put("theme", theme.name)
        put("customBg", customBg); put("customFg", customFg)
        put("brightness", brightness.toDouble())
        put("orientation", orientation.name)
        put("scrollMode", scrollMode.name)
        put("pageAnim", pageAnim.name)
        put("dropCap", dropCap)
        put("pageFrame", pageFrame)
        put("keepOn", keepScreenOn)
        put("autoNight", autoNight)
        put("nightStart", nightStartMin); put("nightEnd", nightEndMin)
        put("exitGuard", exitGuard)
        put("gestures", JSONObject(gestures))
    }.toString()

    companion object {
        private inline fun <reified E : Enum<E>> JSONObject.optEnum(key: String, def: E): E =
            runCatching { enumValueOf<E>(getString(key)) }.getOrDefault(def)

        fun fromJson(s: String): ReaderSettings = runCatching {
            val o = JSONObject(s)
            val d = ReaderSettings()
            val stored = o.optJSONObject("gestures")?.let { g -> g.keys().asSequence().associateWith { g.getString(it) } }
            ReaderSettings(
                fontSize = o.optDouble("fontSize", d.fontSize.toDouble()).toFloat(),
                fontFamily = o.optEnum("fontFamily", d.fontFamily),
                bold = o.optBoolean("bold", d.bold),
                lineSpacing = o.optDouble("lineSpacing", d.lineSpacing.toDouble()).toFloat(),
                width = o.optEnum("width", d.width),
                marginLeft = o.optInt("mL", d.marginLeft),
                marginRight = o.optInt("mR", d.marginRight),
                marginTop = o.optInt("mT", d.marginTop),
                marginBottom = o.optInt("mB", d.marginBottom),
                align = o.optEnum("align", d.align),
                theme = o.optEnum("theme", d.theme),
                customBg = o.optInt("customBg", d.customBg),
                customFg = o.optInt("customFg", d.customFg),
                brightness = o.optDouble("brightness", d.brightness.toDouble()).toFloat(),
                orientation = o.optEnum("orientation", d.orientation),
                scrollMode = o.optEnum("scrollMode", d.scrollMode),
                pageAnim = o.optEnum("pageAnim", d.pageAnim),
                dropCap = o.optBoolean("dropCap", d.dropCap),
                pageFrame = o.optBoolean("pageFrame", d.pageFrame),
                keepScreenOn = o.optBoolean("keepOn", d.keepScreenOn),
                autoNight = o.optBoolean("autoNight", d.autoNight),
                nightStartMin = o.optInt("nightStart", d.nightStartMin),
                nightEndMin = o.optInt("nightEnd", d.nightEndMin),
                exitGuard = o.optBoolean("exitGuard", d.exitGuard),
                gestures = if (stored != null) defaultGestures() + stored else defaultGestures()
            )
        }.getOrDefault(ReaderSettings())
    }
}
