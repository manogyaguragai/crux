package com.crux.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.crux.app.ui.CruxApp
import com.crux.app.ui.theme.CruxTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as CruxApplication).container.settingsRepository
        setContent {
            val deep by settings.deepMode.collectAsState(initial = false)
            val fontScale by settings.fontScale.collectAsState(initial = 1f)
            CruxTheme(deep = deep, fontScale = fontScale) {
                CruxApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The sweep the user actually sees: yesterday's done rows clear on app open
        // (data-model.md). The scheduled WorkManager job arrives with the notifications slice.
        val container = (application as CruxApplication).container
        lifecycleScope.launch {
            container.taskRepository.sweepDoneBeforeToday(ZoneId.systemDefault(), Instant.now())
        }
    }
}
