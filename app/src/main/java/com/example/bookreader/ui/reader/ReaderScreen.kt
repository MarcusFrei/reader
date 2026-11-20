@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bookreader.ui.reader

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bookreader.AppContainer
import com.example.bookreader.data.settings.*
import com.example.bookreader.device.PhoneBrightnessController
import com.example.bookreader.domain.*
import com.example.bookreader.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(bookId: Long, container: AppContainer, onClose: () -> Unit) {
    val vm: ReaderViewModel = viewModel(
        key = "reader-$bookId",
        factory = VMFactory { ReaderViewModel(container, bookId) }
    )
    val load by vm.load.collectAsState()
    val settings by vm.settings.collectAsState()
    val perBook by vm.perBook.collectAsState()
    val book by vm.book.collectAsState()
    val st = load
    val s = settings
    when {
        st is LoadState.Error -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(st.message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose) { Text("Назад в библиотеку") }
        }
        st is LoadState.Ready && s != null ->
            ReaderContent(vm, st, s, perBook, book?.title.orEmpty(), onClose)
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

private fun nextTheme(t: ThemeOpt): ThemeOpt {
    val order = listOf(ThemeOpt.PARCHMENT, ThemeOpt.SEPIA, ThemeOpt.LIGHT, ThemeOpt.DARK)
    val i = order.indexOf(t)
    return if (i < 0) ThemeOpt.PARCHMENT else order[(i + 1) % order.size]
}

@Composable
private fun ReaderContent(
    vm: ReaderViewModel,
    ready: LoadState.Ready,
    s: ReaderSettings,
    perBook: Boolean,
    title: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val content = ready.content
    val total = content.blocks.size
    val fonts = remember { FontSet(context.applicationContext) }
    val body = remember {
        BodyState(ready.startIndex.coerceIn(0, (total - 1).coerceAtLeast(0)), ready.startOffset)
    }

    var showUi by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showGestures by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var liveFont by remember { mutableStateOf<Float?>(null) }
    var liveBrightness by remember { mutableStateOf<Float?>(null) }
    var hud by remember { mutableStateOf<String?>(null) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var sleepFired by remember { mutableStateOf(false) }
    var nowMin by remember { mutableIntStateOf(currentMinutes()) }
    var lastBack by remember { mutableLongStateOf(0L) }
    val brightness = remember(activity) { activity?.let { PhoneBrightnessController(it) } }

    // ── тема: ночной режим по времени ──
    val night = s.autoNight && isInRange(nowMin, s.nightStartMin, s.nightEndMin)
    val colors = s.colors(if (night) ThemeOpt.DARK else s.theme)

    // ── эффекты ──
    LaunchedEffect(Unit) { while (true) { delay(60_000); nowMin = currentMinutes() } }
    LaunchedEffect(hud) { if (hud != null) { delay(900); hud = null } }
    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            sleepFired = false
            delay(sleepMin * 60_000L)
            sleepFired = true
        }
    }
    LaunchedEffect(s.fontSize) { liveFont = null }
    LaunchedEffect(s.brightness) {
        liveBrightness = null
        if (s.brightness >= 0f) brightness?.set(s.brightness) else brightness?.reset()
    }
    DisposableEffect(Unit) { onDispose { brightness?.reset() } }

    LaunchedEffect(s.orientation) {
        activity?.requestedOrientation = when (s.orientation) {
            OrientationOpt.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationOpt.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationOpt.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    val keepOn = s.keepScreenOn && !sleepFired
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    // Скрытие системных панелей на время чтения
    LaunchedEffect(showUi) {
        activity?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            if (showUi) {
                c.show(WindowInsetsCompat.Type.systemBars())
            } else {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    // Запоминание позиции: при листании (с задержкой), при уходе в фон и при закрытии
    LaunchedEffect(body) {
        snapshotFlow { body.block * 100000L + body.char }.collectLatest {
            delay(600)
            vm.savePosition(body.block, body.char, total)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) vm.savePosition(body.block, body.char, total)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            vm.savePosition(body.block, body.char, total)
        }
    }

    // Защита от случайного выхода
    BackHandler {
        if (!s.exitGuard) {
            onClose()
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBack < 2000) {
                onClose()
            } else {
                lastBack = now
                Toast.makeText(context, "Нажмите «Назад» ещё раз, чтобы выйти из книги", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── действия по жестам ──
    val perform: (ReaderAction) -> Unit = { a ->
        when (a) {
            ReaderAction.NEXT_PAGE -> { body.turn(1) }
            ReaderAction.PREV_PAGE -> { body.turn(-1) }
            ReaderAction.TOGGLE_UI -> { showUi = !showUi }
            ReaderAction.ZOOM_TOGGLE -> {
                zoomed = !zoomed
                hud = if (zoomed) "Масштаб ×1.4" else "Масштаб ×1"
            }
            ReaderAction.OPEN_TOC -> { showToc = true }
            ReaderAction.OPEN_SETTINGS -> { showSettings = true }
            ReaderAction.TOGGLE_THEME -> { vm.update { it.copy(theme = nextTheme(it.theme)) } }
            else -> {}
        }
    }
    val performNow by rememberUpdatedState(perform)
    val sNow by rememberUpdatedState(s)

    val onEdgeDelta: (ReaderAction, Float) -> Unit = { a, f ->
        when (a) {
            ReaderAction.BRIGHTNESS -> {
                val base = liveBrightness
                    ?: (if (sNow.brightness >= 0f) sNow.brightness else (brightness?.current() ?: 0.5f))
                val v = (base + f).coerceIn(0.02f, 1f)
                liveBrightness = v
                brightness?.set(v)
                hud = "Яркость ${(v * 100).roundToInt()}%"
            }
            ReaderAction.FONT_SIZE -> {
                val v = ((liveFont ?: sNow.fontSize) + f * 20f).coerceIn(10f, 40f)
                liveFont = v
                hud = "Шрифт ${v.roundToInt()}"
            }
            else -> {}
        }
    }
    val onEdgeEnd: (ReaderAction) -> Unit = { a ->
        when (a) {
            ReaderAction.BRIGHTNESS -> { liveBrightness?.let { b -> vm.update { it.copy(brightness = b) } } }
            ReaderAction.FONT_SIZE -> { liveFont?.let { f -> vm.update { it.copy(fontSize = f) } } }
            else -> {}
        }
    }
    val hasDouble = s.gestures.any { it.key.endsWith("|DOUBLE_TAP") && it.value != ReaderAction.NONE.name }
    val leftAction = s.actionFor(Zone.LEFT_EDGE, Gesture.VERTICAL_DRAG)
    val rightAction = s.actionFor(Zone.RIGHT_EDGE, Gesture.VERTICAL_DRAG)

    // В режиме страниц размер шрифта применяется после отпускания (пересчёт страниц), в ленте — сразу
    val zoom = if (zoomed) 1.4f else 1f
    val fontSp = (if (s.scrollMode == ScrollMode.SCROLL) (liveFont ?: s.fontSize) else s.fontSize) * zoom

    // ── вид ──
    Box(
        Modifier.fillMaxSize()
            .background(colors.bg)
            .onSizeChanged { sz ->
                // Свайпы от краёв не должны конфликтовать с системным жестом «назад»
                if (Build.VERSION.SDK_INT >= 29) {
                    view.systemGestureExclusionRects = listOf(
                        Rect(0, 0, (sz.width * 0.1f).toInt(), sz.height),
                        Rect((sz.width * 0.9f).toInt(), 0, sz.width, sz.height)
                    )
                }
            }
            .pointerInput(hasDouble) {
                val w = size.width.toFloat()
                val doubleTapHandler: ((Offset) -> Unit)? =
                    if (hasDouble) ({ o: Offset -> performNow(sNow.actionFor(zoneOf(o.x, w), Gesture.DOUBLE_TAP)) }) else null
                detectTapGestures(
                    onTap = { o -> performNow(sNow.actionFor(zoneOf(o.x, w), Gesture.TAP)) },
                    onDoubleTap = doubleTapHandler,
                    onLongPress = { o -> performNow(sNow.actionFor(zoneOf(o.x, w), Gesture.LONG_PRESS)) }
                )
            }
    ) {
        if (s.scrollMode == ScrollMode.PAGED) {
            PagedBody(content, s, colors, fontSp, fonts, body)
        } else {
            ScrollBody(content, s, colors, fontSp, body)
        }

        if (leftAction != ReaderAction.NONE) {
            EdgeStrip(Alignment.CenterStart, leftAction, onEdgeDelta, onEdgeEnd)
        }
        if (rightAction != ReaderAction.NONE) {
            EdgeStrip(Alignment.CenterEnd, rightAction, onEdgeDelta, onEdgeEnd)
        }

        hud?.let { text ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xCC000000)
            ) {
                Text(text, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }

        AnimatedVisibility(showUi, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Text(
                        title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { showToc = true }) { Icon(Icons.Filled.Menu, "Оглавление") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Настройки") }
                }
            }
        }

        AnimatedVisibility(showUi, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            var drag by remember { mutableStateOf<Float?>(null) }
            val fraction = body.fraction
            val chapter = content.chapters.lastOrNull { it.blockIndex <= body.block }?.title.orEmpty()
            val mins = ceil(content.remainingWords(body.block) / 200.0).toInt()
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Slider(
                        value = drag ?: fraction,
                        onValueChange = { drag = it },
                        onValueChangeFinished = {
                            val target = drag ?: fraction
                            drag = null
                            body.jumpFraction(target)
                        }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            chapter, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        val pageInfo = if (body.pageText.isNotBlank()) "стр. ${body.pageText} · " else ""
                        Text(
                            "$pageInfo${((drag ?: fraction) * 100).roundToInt()}% · до конца ${formatRemaining(mins)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            s = s, perBook = perBook, sleepMin = sleepMin,
            onDismiss = { showSettings = false },
            onChange = { t -> vm.update(t) },
            onPerBook = { vm.setPerBook(it) },
            onSleep = { sleepMin = it },
            onGestures = { showGestures = true }
        )
    }
    if (showGestures) GestureDialog(s, { t -> vm.update(t) }) { showGestures = false }
    if (showToc) {
        TocDialog(
            chapters = content.chapters,
            onPick = { i ->
                showToc = false
                body.jumpBlock(i)
            },
            onDismiss = { showToc = false }
        )
    }
    if (sleepFired) {
        AlertDialog(
            onDismissRequest = { sleepMin = 0; sleepFired = false },
            title = { Text("Таймер сна") },
            text = { Text("Время вышло. Пора отдыхать.") },
            confirmButton = { TextButton(onClick = onClose) { Text("Закрыть книгу") } },
            dismissButton = { TextButton(onClick = { sleepMin = 0; sleepFired = false }) { Text("Ещё читаю") } }
        )
    }
}

@Composable
private fun BoxScope.EdgeStrip(
    alignment: Alignment,
    action: ReaderAction,
    onDelta: (ReaderAction, Float) -> Unit,
    onEnd: (ReaderAction) -> Unit
) {
    Box(
        Modifier.align(alignment).fillMaxHeight().fillMaxWidth(0.10f)
            .pointerInput(action) {
                detectVerticalDragGestures(
                    onDragEnd = { onEnd(action) },
                    onDragCancel = { onEnd(action) }
                ) { change, dy ->
                    change.consume()
                    onDelta(action, -dy / size.height)
                }
            }
    )
}
