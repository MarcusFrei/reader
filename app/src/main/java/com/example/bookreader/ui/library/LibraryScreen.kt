@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.bookreader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bookreader.AppContainer
import com.example.bookreader.data.BookEntity
import com.example.bookreader.ui.reader.AppFonts
import com.example.bookreader.ui.theme.Medieval
import com.example.bookreader.ui.theme.OrnamentDivider
import com.example.bookreader.ui.theme.ParchmentBox
import com.example.bookreader.util.VMFactory
import java.io.File
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(container: AppContainer, onOpen: (Long) -> Unit) {
    val vm: LibraryViewModel = viewModel(factory = VMFactory { LibraryViewModel(container) })
    val books by vm.books.collectAsState()
    val last by vm.lastRead.collectAsState()
    val query by vm.query.collectAsState()
    val sort by vm.sort.collectAsState()
    val filter by vm.filter.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var menuBook by remember { mutableStateOf<BookEntity?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.import(uris)
    }

    ParchmentBox(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Читалка", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Text("книжная палата", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                        Box {
                            TextButton(onClick = { sortMenu = true }) { Text(sort.label) }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortOpt.entries.forEach { o ->
                                    DropdownMenuItem(
                                        text = { Text(o.label) },
                                        onClick = { vm.sort.value = o; sortMenu = false }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { about = true }) {
                            Icon(Icons.Filled.Info, contentDescription = "О программе", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                    OrnamentDivider(Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Добавить") }
                )
            }
        ) { pad ->
            Column(Modifier.padding(pad)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    singleLine = true,
                    placeholder = { Text("Поиск по названию и автору") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(FilterOpt.entries) { f ->
                        FilterChip(selected = f == filter, onClick = { vm.filter.value = f }, label = { Text(f.label) })
                    }
                }
                last?.let { b -> if (query.isBlank()) ContinueCard(b) { onOpen(b.id) } }

                if (books.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            OrnamentDivider(Modifier.width(160.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Полки пока пусты.\nНажмите «Добавить» и выберите книги: EPUB, FB2, TXT\nили архивы ZIP, 7z, RAR.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(12.dp))
                            OrnamentDivider(Modifier.width(160.dp))
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(books, key = { it.id }) { b ->
                            BookCard(b, onClick = { onOpen(b.id) }, onLong = { menuBook = b })
                        }
                    }
                }
            }
        }
    }

    menuBook?.let { BookMenu(it, vm) { menuBook = null } }
    if (about) AboutDialog { about = false }
}

@Composable
private fun ContinueCard(b: BookEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Medieval.Leather, contentColor = Medieval.ParchmentLight),
        border = BorderStroke(1.dp, Medieval.GoldLight)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Продолжить чтение", style = MaterialTheme.typography.labelMedium, color = Medieval.GoldLight)
                Text(
                    b.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                LinearProgressIndicator(
                    progress = { b.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = Medieval.GoldLight,
                    trackColor = Color(0x44D6A93E)
                )
            }
            Text(
                "${(b.progress * 100).roundToInt()}%",
                modifier = Modifier.padding(start = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Medieval.GoldLight
            )
        }
    }
}

@Composable
private fun BookCard(b: BookEntity, onClick: () -> Unit, onLong: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .shadow(6.dp, shape)
                .clip(shape)
                .border(1.dp, Medieval.Gold.copy(alpha = 0.85f), shape)
        ) {
            if (b.coverPath != null) {
                AsyncImage(
                    model = File(b.coverPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LeatherCover(b.title, b.author)
            }
            // Тень корешка
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(14.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0x77000000), Color.Transparent)))
            )
            if (b.isFavorite) {
                Icon(
                    Icons.Filled.Favorite, contentDescription = null, tint = Medieval.GoldLight,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp)
                )
            }
            if (b.progress > 0f) {
                LinearProgressIndicator(
                    progress = { b.progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Medieval.GoldLight,
                    trackColor = Color(0x66000000)
                )
            }
        }
        Text(
            b.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
        b.author?.let {
            Text(
                it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Обложка-«переплёт» для книг без своей обложки: кожа, золотое тиснение, рамка. */
@Composable
private fun LeatherCover(title: String, author: String?) {
    val palette = listOf(0xFF6E1F1B, 0xFF1F3D2B, 0xFF1E2A44, 0xFF4A2C17, 0xFF3F1F3F, 0xFF4A3A12)
    val base = Color(palette[(title.hashCode() and 0x7fffffff) % palette.size])
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(lerp(base, Color.White, 0.08f), base, lerp(base, Color.Black, 0.4f)))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val a = 9.dp.toPx()
            drawRect(
                Medieval.GoldLight.copy(alpha = 0.85f), Offset(a, a),
                Size(size.width - 2 * a, size.height - 2 * a), style = Stroke(1.2.dp.toPx())
            )
            val b = a + 4.dp.toPx()
            drawRect(
                Medieval.GoldLight.copy(alpha = 0.45f), Offset(b, b),
                Size(size.width - 2 * b, size.height - 2 * b), style = Stroke(0.7.dp.toPx())
            )
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OrnamentDivider(color = Medieval.GoldLight)
            Spacer(Modifier.height(6.dp))
            Text(
                title, color = Medieval.GoldLight, textAlign = TextAlign.Center,
                fontFamily = AppFonts.Head, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp,
                maxLines = 5, overflow = TextOverflow.Ellipsis
            )
            if (!author.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    author, color = Medieval.GoldLight.copy(alpha = 0.8f), textAlign = TextAlign.Center,
                    fontFamily = AppFonts.Garamond, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            OrnamentDivider(color = Medieval.GoldLight)
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
    val name = info?.versionName ?: "?"
    val code = info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Читалка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrnamentDivider()
                Text("Версия $name (сборка $code)", style = MaterialTheme.typography.bodyLarge)
                Text("Форматы: EPUB, FB2, TXT. Архивы: ZIP, 7z, RAR (RAR4).", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Обновление: скачайте новый APK и установите поверх — библиотека, закладки и настройки сохранятся.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun BookMenu(book: BookEntity, vm: LibraryViewModel, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf("menu") }
    when (mode) {
        "edit" -> {
            var t by remember { mutableStateOf(book.title) }
            var a by remember { mutableStateOf(book.author ?: "") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Изменить информацию") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(t, { t = it }, label = { Text("Название") })
                        OutlinedTextField(a, { a = it }, label = { Text("Автор") })
                    }
                },
                confirmButton = { TextButton(onClick = { vm.edit(book, t, a); onDismiss() }) { Text("Сохранить") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
            )
        }
        "delete" -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Удалить книгу?") },
            text = { Text(book.title) },
            confirmButton = { TextButton(onClick = { vm.delete(book); onDismiss() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    MenuItem(if (book.isFavorite) "Убрать из избранного" else "В избранное") { vm.toggleFavorite(book); onDismiss() }
                    MenuItem("Статус: не начата") { vm.setStatus(book, "new"); onDismiss() }
                    MenuItem("Статус: читаю") { vm.setStatus(book, "reading"); onDismiss() }
                    MenuItem("Статус: прочитана") { vm.setStatus(book, "finished"); onDismiss() }
                    MenuItem("Изменить название и автора") { mode = "edit" }
                    MenuItem(if (book.isHidden) "Показать в библиотеке" else "Скрыть из библиотеки") { vm.toggleHidden(book); onDismiss() }
                    MenuItem("Удалить") { mode = "delete" }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}
