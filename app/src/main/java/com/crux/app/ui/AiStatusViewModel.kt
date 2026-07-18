package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.AppContainer
import com.crux.app.intelligence.AiErrorKind
import com.crux.app.ui.components.AiPresence
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Feeds the ambient AI status icon (in every tab header). [presence] folds "is AI on+keyed" and "is a
 * call in flight" into the icon's three states; [notice] turns each failure signal from the chain into
 * a short, self-clearing message the icon breathes out. Both come straight from the shared
 * [com.crux.app.intelligence.Intelligence] instance, so one source drives the indicator everywhere.
 */
class AiStatusViewModel(container: AppContainer) : ViewModel() {

    private val intelligence = container.intelligence

    val presence: StateFlow<AiPresence> =
        combine(intelligence.active, intelligence.busy) { active, busy ->
            when {
                !active -> AiPresence.OFF
                busy -> AiPresence.BUSY
                else -> AiPresence.IDLE
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiPresence.OFF)

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            // collectLatest: a fresh failure resets the visible timer rather than queueing.
            intelligence.notices.collectLatest { kind ->
                _notice.value = messageFor(kind)
                delay(NOTICE_MS)
                _notice.value = null
            }
        }
    }

    companion object {
        private const val NOTICE_MS = 4_000L

        private fun messageFor(kind: AiErrorKind): String = when (kind) {
            AiErrorKind.QUOTA -> Copy.AI_NOTICE_QUOTA
            AiErrorKind.RATE_LIMIT -> Copy.AI_NOTICE_RATE
            AiErrorKind.NETWORK -> Copy.AI_NOTICE_NETWORK
            AiErrorKind.AUTH -> Copy.AI_NOTICE_AUTH
            AiErrorKind.FAILED -> Copy.AI_NOTICE_FAILED
        }

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AiStatusViewModel(container) }
        }
    }
}
