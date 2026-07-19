package com.crux.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.crux.app.ui.CruxApp
import com.crux.app.ui.components.CruxSplash
import com.crux.app.ui.theme.CruxTheme
import kotlinx.coroutines.delay
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
                // POST_NOTIFICATIONS is a runtime permission on Android 13+. Without it every
                // notification — the morning/wrap digests AND per-task reminders — silently no-ops,
                // which is exactly why they "didn't come through". Ask once, from a LaunchedEffect
                // (after the activity is resumed) so the OS dialog reliably surfaces on a fresh grant.
                val context = LocalContext.current
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* granted or not; if denied, settings offers a way back in */ }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // cold-start splash: the stones assemble over the void while the app composes behind
                // it, then the whole splash cross-fades away to reveal the loaded app. The floor lets a
                // full assembly + settle play; the 500 ms fade is the smooth hand-off the owner asked for.
                var ready by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(1400)
                    ready = true
                }
                Box(Modifier.fillMaxSize()) {
                    CruxApp()
                    AnimatedVisibility(
                        visible = !ready,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(500)),
                    ) {
                        CruxSplash()
                    }
                }
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
