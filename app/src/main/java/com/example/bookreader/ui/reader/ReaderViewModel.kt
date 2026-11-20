package com.example.bookreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookreader.AppContainer
import com.example.bookreader.data.BookContent
import com.example.bookreader.data.BookEntity
import com.example.bookreader.data.BookParser
import com.example.bookreader.data.settings.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val content: BookContent, val startIndex: Int, val startOffset: Int) : LoadState
    data class Error(val message: String) : LoadState
}

class ReaderViewModel(private val c: AppContainer, private val bookId: Long) : ViewModel() {
    private val dao = c.db.bookDao()

    val book: StateFlow<BookEntity?> =
        dao.observe(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val global: StateFlow<ReaderSettings?> =
        c.settings.settings.map<ReaderSettings, ReaderSettings?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Эффективные настройки: индивидуальные для книги, иначе глобальные. */
    val settings: StateFlow<ReaderSettings?> = combine(book, global) { b, g ->
        if (g == null) null else (b?.settingsJson?.let { ReaderSettings.fromJson(it) } ?: g)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val perBook: StateFlow<Boolean> = book.map { it?.settingsJson != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _load = MutableStateFlow<LoadState>(LoadState.Loading)
    val load: StateFlow<LoadState> = _load

    init {
        viewModelScope.launch {
            val b = dao.get(bookId)
            if (b == null) {
                _load.value = LoadState.Error("Книга не найдена")
                return@launch
            }
            runCatching {
                withContext(Dispatchers.Default) { BookParser.content(File(b.filePath), b.format, b.title) }
            }.onSuccess { content ->
                if (content.blocks.isEmpty()) {
                    _load.value = LoadState.Error("В книге не найден текст")
                } else {
                    _load.value = LoadState.Ready(content, b.lastIndex, b.lastOffset)
                    dao.touch(bookId, System.currentTimeMillis())
                }
            }.onFailure {
                _load.value = LoadState.Error("Не удалось открыть книгу: ${it.message}")
            }
        }
    }

    fun update(t: (ReaderSettings) -> ReaderSettings) {
        val cur = settings.value ?: return
        val new = t(cur)
        viewModelScope.launch {
            if (book.value?.settingsJson != null) dao.saveSettings(bookId, new.toJson())
            else c.settings.save(new)
        }
    }

    fun setPerBook(on: Boolean) {
        val cur = settings.value ?: return
        viewModelScope.launch { dao.saveSettings(bookId, if (on) cur.toJson() else null) }
    }

    /** Сохраняем в appScope, чтобы запись не отменилась при закрытии экрана. */
    fun savePosition(index: Int, offset: Int, total: Int) {
        if (total <= 0) return
        val progress = if (total <= 1) 0f else index.toFloat() / (total - 1)
        val cur = book.value?.status ?: "new"
        val status = when {
            progress >= 0.98f -> "finished"
            index > 0 -> "reading"
            else -> cur
        }
        c.appScope.launch { dao.savePosition(bookId, index, offset, progress, status, System.currentTimeMillis()) }
    }
}
