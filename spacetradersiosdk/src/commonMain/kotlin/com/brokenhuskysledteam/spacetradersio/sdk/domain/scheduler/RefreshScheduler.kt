package com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RefreshScheduler(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System
) {

    private val _activeTimers = MutableStateFlow<Map<String, ScheduledRefresh>>(emptyMap())
    val activeTimers: StateFlow<Map<String, ScheduledRefresh>> = _activeTimers.asStateFlow()

    private var loopJob: Job? = null

    fun schedule(id: String, expiresAt: Instant, action: suspend () -> Unit) {
        _activeTimers.update { it + (id to ScheduledRefresh(id, expiresAt, action)) }
        restartLoop()
    }

    fun cancel(id: String) {
        _activeTimers.update { it - id }
        restartLoop()
    }

    fun cancelByPrefix(prefix: String) {
        _activeTimers.update { map -> map.filterKeys { !it.startsWith(prefix) } }
        restartLoop()
    }

    fun onResume() {
        val now = clock.now()
        val expired = _activeTimers.value.filter { (_, entry) ->
            entry.expiresAt + 1.seconds <= now
        }
        if (expired.isNotEmpty()) {
            _activeTimers.update { it - expired.keys }
            expired.values.forEach { entry ->
                scope.launch {
                    try {
                        entry.action()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Napier.e("RefreshScheduler: action '${entry.id}' failed on resume", e)
                    }
                }
            }
        }
        restartLoop()
    }

    private fun restartLoop() {
        loopJob?.cancel()
        loopJob = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        while (true) {
            val entries = _activeTimers.value
            if (entries.isEmpty()) return

            val nearest = entries.values.minBy { it.expiresAt }
            val now = clock.now()
            val waitDuration = (nearest.expiresAt + 1.seconds) - now

            if (waitDuration.isPositive()) {
                delay(waitDuration)
            }

            // Re-check after delay: the entry may have been cancelled
            if (!_activeTimers.value.containsKey(nearest.id)) continue

            _activeTimers.update { it - nearest.id }
            try {
                nearest.action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("RefreshScheduler: action '${nearest.id}' failed", e)
            }
        }
    }
}
