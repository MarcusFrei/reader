package com.example.bookreader

import android.app.Application
import androidx.room.Room
import com.example.bookreader.data.ALL_MIGRATIONS
import com.example.bookreader.data.AppDatabase
import com.example.bookreader.data.BookStore
import com.example.bookreader.data.settings.SettingsRepository
import com.example.bookreader.device.DeviceCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ReaderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Простой DI-контейнер (Hilt добавим позже, когда проект вырастет). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "reader.db")
        .addMigrations(*ALL_MIGRATIONS)
        .build()
    val settings = SettingsRepository(app)
    val store = BookStore(app, db.bookDao())
    val capabilities = DeviceCapabilities(app)
}
