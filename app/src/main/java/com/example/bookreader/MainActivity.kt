package com.example.bookreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bookreader.ui.library.LibraryScreen
import com.example.bookreader.ui.reader.ReaderScreen
import com.example.bookreader.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as ReaderApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "library") {
                        composable("library") {
                            LibraryScreen(container) { id -> nav.navigate("reader/$id") }
                        }
                        composable(
                            "reader/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { entry ->
                            ReaderScreen(entry.arguments?.getLong("id") ?: 0L, container) { nav.popBackStack() }
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Импорт через «Поделиться» и «Открыть с помощью». */
    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val uris: List<Uri> = when (i.action) {
            Intent.ACTION_VIEW -> listOfNotNull(i.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val msg = container.store.importAll(uris)
            if (msg.isNotEmpty()) Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
}
