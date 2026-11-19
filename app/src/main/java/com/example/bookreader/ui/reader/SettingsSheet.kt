@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bookreader.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookreader.data.ChapterRef
import com.example.bookreader.data.settings.*
import com.example.bookreader.domain.*
import kotlin.math.roundToInt

typealias SettingsChange = ((ReaderSettings) -> ReaderSettings) -> Unit

@Composable
fun SettingsSheet(
    s: ReaderSettings,
    perBook: Boolean,
    sleepMin: Int,
    onDismiss: () -> Unit,
    onChange: SettingsChange,
    onPerBook: (Boolean) -> Unit,
    onSleep: (Int) -> Unit,
    onGestures: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SwitchRow("Только для этой книги", perBook, onPerBook)

            Section("Шрифт")
            SliderRow("Размер", s.fontSize, 10f..40f, { "${it.roundToInt()}" }) { v -> onChange { it.copy(fontSize = v) } }
            Chips("Семейство", FontFamilyOpt.entries, s.fontFamily, { it.label }) { v -> onChange { it.copy(fontFamily = v) } }
            SwitchRow("Жирное начертание", s.bold) { v -> onChange { it.copy(bold = v) } }

            Section("Макет")
            SliderRow("Межстрочный интервал", s.lineSpacing, 1.0f..2.0f, { "%.2f".format(it) }) { v -> onChange { it.copy(lineSpacing = v) } }
            Chips("Ширина текста", WidthOpt.entries, s.width, { it.label }) { v -> onChange { it.copy(width = v) } }
            SliderRow("Поле слева", s.marginLeft.toFloat(), 0f..64f, { "${it.roundToInt()}" }) { v -> onChange { it.copy(marginLeft = v.roundToInt()) } }
            SliderRow("Поле справа", s.marginRight.toFloat(), 0f..64f, { "${it.roundToInt()}" }) { v -> onChange { it.copy(marginRight = v.roundToInt()) } }
            SliderRow("Поле сверху", s.marginTop.toFloat(), 0f..96f, { "${it.roundToInt()}" }) { v -> onChange { it.copy(marginTop = v.roundToInt()) } }
            SliderRow("Поле снизу", s.marginBottom.toFloat(), 0f..96f, { "${it.roundToInt()}" }) { v -> onChange { it.copy(marginBottom = v.roundToInt()) } }
            Chips("Выравнивание", AlignOpt.entries, s.align, { it.label }) { v -> onChange { it.copy(align = v) } }

            Section("Тема и яркость")
            Chips("Тема", ThemeOpt.entries, s.theme, { it.label }) { v -> onChange { it.copy(theme = v) } }
            if (s.theme == ThemeOpt.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HexField("Фон (RRGGBB)", s.customBg, Modifier.weight(1f)) { c -> onChange { it.copy(customBg = c) } }
                    HexField("Текст (RRGGBB)", s.customFg, Modifier.weight(1f)) { c -> onChange { it.copy(customFg = c) } }
                }
            }
            SwitchRow("Системная яркость", s.brightness < 0f) { on ->
                onChange { it.copy(brightness = if (on) -1f else 0.6f) }
            }
            if (s.brightness >= 0f) {
                SliderRow("Яркость", s.brightness, 0.02f..1f, { "${(it * 100).roundToInt()}%" }) { v -> onChange { it.copy(brightness = v) } }
            }
            SwitchRow("Ночная тема по времени", s.autoNight) { v -> onChange { it.copy(autoNight = v) } }
            if (s.autoNight) {
                SliderRow("Начало", (s.nightStartMin / 60).toFloat(), 0f..23f, { "${it.roundToInt()}:00" }, 22) { v ->
                    onChange { it.copy(nightStartMin = v.roundToInt() * 60) }
                }
                SliderRow("Конец", (s.nightEndMin / 60).toFloat(), 0f..23f, { "${it.roundToInt()}:00" }, 22) { v ->
                    onChange { it.copy(nightEndMin = v.roundToInt() * 60) }
                }
            }

            Section("Режим чтения")
            Chips("Ориентация", OrientationOpt.entries, s.orientation, { it.label }) { v -> onChange { it.copy(orientation = v) } }
            Chips("Прокрутка", ScrollMode.entries, s.scrollMode, { it.label }) { v -> onChange { it.copy(scrollMode = v) } }
            Chips("Анимация страниц", PageAnim.entries, s.pageAnim, { it.label }) { v -> onChange { it.copy(pageAnim = v) } }
            SwitchRow("Не выключать экран", s.keepScreenOn) { v -> onChange { it.copy(keepScreenOn = v) } }
            SwitchRow("Защита от случайного выхода", s.exitGuard) { v -> onChange { it.copy(exitGuard = v) } }
            Chips("Таймер сна", listOf(0, 15, 30, 45, 60), sleepMin, { if (it == 0) "Выкл" else "$it мин" }, onSleep)

            Button(onClick = onGestures, modifier = Modifier.fillMaxWidth()) { Text("Настроить жесты") }
        }
    }
}

@Composable
fun TocDialog(chapters: List<ChapterRef>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оглавление") },
        text = {
            LazyColumn {
                items(chapters.size) { i ->
                    TextButton(onClick = { onPick(chapters[i].blockIndex) }, modifier = Modifier.fillMaxWidth()) {
                        Text(chapters[i].title, maxLines = 2, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
fun GestureDialog(s: ReaderSettings, onChange: SettingsChange, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Жесты") },
        text = {
            LazyColumn {
                Zone.entries.forEach { z ->
                    item {
                        Text(z.label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    }
                    Gesture.entries.forEach { g ->
                        item { GestureRow(z, g, s, onChange) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = { TextButton(onClick = { onChange { it.copy(gestures = defaultGestures()) } }) { Text("Сбросить") } }
    )
}

@Composable
private fun GestureRow(z: Zone, g: Gesture, s: ReaderSettings, onChange: SettingsChange) {
    var open by remember { mutableStateOf(false) }
    val current = s.actionFor(z, g)
    val options = ReaderAction.entries.filter {
        if (g == Gesture.VERTICAL_DRAG) it.continuous || it == ReaderAction.NONE else !it.continuous
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(g.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { open = true }) { Text(current.label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.label) },
                        onClick = {
                            onChange { it.copy(gestures = it.gestures + (gestureKey(z, g) to a.name)) }
                            open = false
                        }
                    )
                }
            }
        }
    }
}

// ───────── мелкие компоненты ─────────

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun <T> Chips(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
            }
        }
    }
}

/** Значение применяется при отпускании ползунка, чтобы не писать в хранилище на каждый пиксель. */
@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    steps: Int = 0,
    onDone: (Float) -> Unit
) {
    var tmp by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(display(tmp))
        }
        Slider(
            value = tmp,
            onValueChange = { tmp = it },
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { onDone(tmp) }
        )
    }
}

@Composable
private fun HexField(label: String, color: Int, modifier: Modifier, onValid: (Int) -> Unit) {
    var text by remember(color) { mutableStateOf("%06X".format(color and 0xFFFFFF)) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.uppercase().take(6)
            if (text.length == 6) text.toIntOrNull(16)?.let { onValid(it or (0xFF shl 24)) }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}
