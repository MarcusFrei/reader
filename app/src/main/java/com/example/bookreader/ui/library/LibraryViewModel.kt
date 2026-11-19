package com.example.bookreader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookreader.AppContainer
import com.example.bookreader.data.BookEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOpt(val label: String) {
    ADDED("По дате добавления"),
    LAST_READ("По последнему чтению"),
    TITLE("По названию"),
    AUTHOR("По автору")
}

enum class FilterOpt(val label: String) {
    ALL("Все"), FAVORITES("Избранное"), READING("Читаю"), FINISHED("Прочитано"), HIDDEN("Скрытые")
}

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    private val dao = c.db.bookDao()

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortOpt.ADDED)
    val filter = MutableStateFlow(FilterOpt.ALL)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val all = dao.observeAll()

    val books: StateFlow<List<BookEntity>> = combine(all, query, sort, filter) { list, q, so, fi ->
        var r = when (fi) {
            FilterOpt.ALL -> list.filter { !it.isHidden }
            FilterOpt.FAVORITES -> list.filter { it.isFavorite && !it.isHidden }
            FilterOpt.READING -> list.filter { it.status == "reading" && !it.isHidden }
            FilterOpt.FINISHED -> list.filter { it.status == "finished" && !it.isHidden }
            FilterOpt.HIDDEN -> list.filter { it.isHidden }
        }
        if (q.isNotBlank()) {
            r = r.filter { it.title.contains(q, true) || it.author?.contains(q, true) == true }
        }
        when (so) {
            SortOpt.ADDED -> r.sortedByDescending { it.addedAt }
            SortOpt.LAST_READ -> r.sortedByDescending { it.lastOpenedAt ?: 0L }
            SortOpt.TITLE -> r.sortedBy { it.title.lowercase() }
            SortOpt.AUTHOR -> r.sortedBy { (it.author ?: "").lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Quick Resume: последняя открытая книга. */
    val lastRead: StateFlow<BookEntity?> = all.map { l ->
        l.filter { !it.isHidden && it.lastOpenedAt != null }.maxByOrNull { it.lastOpenedAt ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch { messages.emit(c.store.importAll(uris)) }
    }

    fun toggleFavorite(b: BookEntity) = viewModelScope.launch { dao.update(b.copy(isFavorite = !b.isFavorite)) }
    fun toggleHidden(b: BookEntity) = viewModelScope.launch { dao.update(b.copy(isHidden = !b.isHidden)) }
    fun setStatus(b: BookEntity, status: String) = viewModelScope.launch { dao.update(b.copy(status = status)) }
    fun edit(b: BookEntity, title: String, author: String) = viewModelScope.launch {
        dao.update(b.copy(title = title.ifBlank { b.title }, author = author.ifBlank { null }))
    }
    fun delete(b: BookEntity) = viewModelScope.launch { c.store.delete(b) }
}
