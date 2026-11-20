package com.example.bookreader.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val lastIndex: Int = 0,        // позиция чтения: индекс блока текста
    @ColumnInfo(defaultValue = "0") val lastOffset: Int = 0, // и смещение символа внутри блока (v2)
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

    @Query("UPDATE books SET lastIndex = :index, lastOffset = :offset, progress = :progress, status = :status, lastOpenedAt = :now WHERE id = :id")
    suspend fun savePosition(id: Long, index: Int, offset: Int, progress: Float, status: String, now: Long)

    @Query("UPDATE books SET settingsJson = :json WHERE id = :id")
    suspend fun saveSettings(id: Long, json: String?)

    @Query("UPDATE books SET lastOpenedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)
}

@Database(entities = [BookEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}

/**
 * Миграции БД. Правило: при каждом изменении схемы поднимаем version
 * и добавляем сюда миграцию, чтобы библиотека пользователя переживала обновления.
 * fallbackToDestructiveMigration() НЕ используется намеренно.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN lastOffset INTEGER NOT NULL DEFAULT 0")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
