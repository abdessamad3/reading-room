package com.readingroom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private lateinit var lock: LockController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lock = LockController(this)

        // Keep the lock in step with the setting, and restore it whenever we come back to the screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.state.map { it.settings.lockEnabled }.distinctUntilChanged().collect { enabled ->
                    if (enabled) lock.engage() else lock.release()
                }
            }
        }

        setContent {
            ReadingRoomTheme {
                AppRoot(viewModel = viewModel, lock = lock)
            }
        }
    }
}
