package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HighlightConfig(
    val flashCount: Int = 3,
    val flashDuration: Long = 260L,
)

data class HighlightState(
    val highLightKey: String,
    val config: HighlightConfig,
    val startTime: Long = System.currentTimeMillis(),
)

class HighlightManager {
    private val _highlights = MutableStateFlow<Map<String, HighlightState>>(emptyMap())
    val highlights: StateFlow<Map<String, HighlightState>> = _highlights
    private val jobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    fun addHighlight(key: String, config: HighlightConfig = HighlightConfig()) {
        if (key.isBlank()) {
            return
        }
        jobs.remove(key)?.cancel()
        val currentHighlights = _highlights.value.toMutableMap()
        currentHighlights[key] = HighlightState(
            highLightKey = key,
            config = config,
            startTime = System.currentTimeMillis()
        )
        _highlights.value = currentHighlights
        jobs[key] = scope.launch {
            delay(config.flashDuration * config.flashCount * 2 + 100)
            removeHighlight(key)
        }
    }

    fun removeHighlight(key: String) {
        jobs.remove(key)?.cancel()
        val currentHighlights = _highlights.value.toMutableMap()
        if (currentHighlights.remove(key) != null) {
            _highlights.value = currentHighlights
        }
    }

    fun clearAllHighlights() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _highlights.value = emptyMap()
    }

    fun getHighlight(key: String): HighlightState? {
        return _highlights.value[key]
    }

    fun isHighlighted(key: String): Boolean {
        return _highlights.value.containsKey(key)
    }
}
