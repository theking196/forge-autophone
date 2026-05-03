package com.forge.autophone.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class ActionEntry(
    val id: Long,
    val tool: String,
    val args: String,
    val ok: Boolean,
    val output: String,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val timeLabel: String get() =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * In-memory ring-buffer of the last [MAX_ENTRIES] tool executions.
 * Written by [AutoPhoneAccessibilityService] / [AutoPhoneBinderService];
 * read by [ActionLogFragment] and [DashboardFragment].
 */
class ActionLogRepository(private val maxEntries: Int = 200) {

    private val _entries = MutableStateFlow<List<ActionEntry>>(emptyList())
    val entries: StateFlow<List<ActionEntry>> = _entries

    private val counter = AtomicLong(0)

    fun record(tool: String, args: String, ok: Boolean, output: String, durationMs: Long) {
        val entry = ActionEntry(counter.incrementAndGet(), tool, args, ok, output, durationMs)
        val current = _entries.value.toMutableList()
        current.add(0, entry)
        if (current.size > maxEntries) current.subList(maxEntries, current.size).clear()
        _entries.value = current
    }

    val todayCount: Int get() {
        val startOfDay = run {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0); cal.timeInMillis
        }
        return _entries.value.count { it.timestamp >= startOfDay }
    }

    val successRate: Int get() {
        val all = _entries.value
        if (all.isEmpty()) return 100
        return (all.count { it.ok } * 100 / all.size)
    }
}
