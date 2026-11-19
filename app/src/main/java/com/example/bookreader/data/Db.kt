package com.example.bookreader.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val format: String,            // epub, fb2, txt
    val filePath: String,
    val coverPath: String?,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val status: String = "new",    // new, reading, finished
    val rating: Int? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val progress: Float = 0f,
    val lastIndex: Int = 0,        // индекс блока текста (позиция чтения)
    val settingsJson: String? = null // индивидуальные настройки книги
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("UPDATE books SET lastIndex = :index, progress = :progress, status = :status, lastOpenedAt = :now WHERE id = :id")
    suspend fun savePosition(id: Long, index: Int, progress: Float, status: String, now: Long)

    @Query("UPDATE books SET settingsJson = :json WHERE id = :id")
    suspend fun saveSettings(id: Long, json: String?)

    @Query("UPDATE books SET lastOpenedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)
}

@Database(entities = [BookEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
