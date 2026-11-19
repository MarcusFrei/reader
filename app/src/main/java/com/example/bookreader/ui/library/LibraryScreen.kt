@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.bookreader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bookreader.AppContainer
import com.example.bookreader.data.BookEntity
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

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.import(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Библиотека") },
                actions = {
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
                }
            )
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true,
                placeholder = { Text("Поиск по названию и автору") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FilterOpt.entries) { f ->
                    FilterChip(selected = f == filter, onClick = { vm.filter.value = f }, label = { Text(f.label) })
                }
            }
            last?.let { b -> if (query.isBlank()) ContinueCard(b) { onOpen(b.id) } }

            if (books.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Здесь пока пусто.\nНажмите «Добавить» и выберите EPUB, FB2 или TXT.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(books, key = { it.id }) { b ->
                        BookCard(b, onClick = { onOpen(b.id) }, onLong = { menuBook = b })
                    }
                }
            }
        }
    }

    menuBook?.let { BookMenu(it, vm) { menuBook = null } }
}

@Composable
private fun ContinueCard(b: BookEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Продолжить чтение", style = MaterialTheme.typography.labelMedium)
                Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { b.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
            Text("${(b.progress * 100).roundToInt()}%", modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun BookCard(b: BookEntity, onClick: () -> Unit, onLong: () -> Unit) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (b.coverPath != null) {
                AsyncImage(
                    model = File(b.coverPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    b.title, maxLines = 5, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp)
                )
            }
            if (b.isFavorite) {
                Icon(
                    Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                )
            }
            if (b.progress > 0f) {
                LinearProgressIndicator(
                    progress = { b.progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                )
            }
        }
        Text(b.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp))
        b.author?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
