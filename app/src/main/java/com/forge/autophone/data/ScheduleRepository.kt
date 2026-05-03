package com.forge.autophone.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TriggerType { TIME, EVENT }

enum class RepeatMode {
    ONCE, DAILY, WEEKDAYS, WEEKEND, WEEKLY;
    val label: String get() = when (this) {
        ONCE     -> "Once"
        DAILY    -> "Daily"
        WEEKDAYS -> "Weekdays"
        WEEKEND  -> "Weekends"
        WEEKLY   -> "Weekly"
    }
}

enum class EventTrigger(val label: String) {
    BATTERY_LOW    ("Battery low"),
    WIFI_CONNECTED ("Wi-Fi connected"),
    SCREEN_ON      ("Screen unlocked"),
    CHARGING       ("Charging started"),
    APP_OPENED     ("App opened"),
}

data class Schedule(
    val id: String                  = UUID.randomUUID().toString(),
    val name: String,
    val triggerType: TriggerType,
    val timeHour: Int               = 8,
    val timeMinute: Int             = 0,
    val repeatMode: RepeatMode      = RepeatMode.DAILY,
    val eventTrigger: EventTrigger? = null,
    val eventAppPackage: String?    = null,
    val actionPlan: String,
    val isEnabled: Boolean          = true,
    val lastRunAt: Long?            = null,
    val lastRunOk: Boolean?         = null,
    val lastRunResult: String?      = null,
    val runCount: Int               = 0,
) {
    val triggerLabel: String get() = when (triggerType) {
        TriggerType.TIME ->
            "${timeHour.toString().padStart(2,'0')}:${timeMinute.toString().padStart(2,'0')} · ${repeatMode.label}"
        TriggerType.EVENT -> eventTrigger?.label ?: "Unknown event"
    }

    val lastRunLabel: String get() = when {
        lastRunAt == null   -> "Never run"
        lastRunOk == true   -> "✓ " + SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date(lastRunAt))
        else                -> "✗ " + SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date(lastRunAt))
    }
}

/**
 * Persists and exposes the list of user-defined automation schedules.
 *
 * ## Live execution state
 *
 * When Forge OS begins executing a plan it calls [markRunning] (via
 * [AutoPhoneBinderService.notifyScheduleStarted]). This adds the schedule's id
 * to [runningSchedules] — a [StateFlow] of [RunningEntry] objects. The
 * Schedules screen collects this flow and shows an animated "Running…" chip on
 * the matching card.
 *
 * When Forge OS finishes it calls [markCompleted] which removes the entry from
 * [runningSchedules] and persists the last-run result so the "✓ Today 08:01"
 * label is correct on next launch.
 */
class ScheduleRepository(context: Context) {

    data class RunningEntry(
        val scheduleId: String,
        val planSummary: String,
        val startedAt: Long = System.currentTimeMillis(),
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("forge_schedules", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _schedules = MutableStateFlow(load())
    val schedules: StateFlow<List<Schedule>> = _schedules

    /** Set of schedules that Forge OS is actively executing right now. */
    private val _runningSchedules = MutableStateFlow<List<RunningEntry>>(emptyList())
    val runningSchedules: StateFlow<List<RunningEntry>> = _runningSchedules

    // ── CRUD ─────────────────────────────────────────────────────────────────

    fun add(schedule: Schedule) = update(_schedules.value + schedule)

    fun replace(schedule: Schedule) =
        update(_schedules.value.map { if (it.id == schedule.id) schedule else it })

    fun delete(id: String) = update(_schedules.value.filter { it.id != id })

    fun setEnabled(id: String, enabled: Boolean) {
        replace(_schedules.value.first { it.id == id }.copy(isEnabled = enabled))
    }

    /** Record a manual "Run now" result (from inside AutoPhone). */
    fun recordRun(id: String, ok: Boolean) {
        val s = _schedules.value.firstOrNull { it.id == id } ?: return
        replace(s.copy(
            lastRunAt     = System.currentTimeMillis(),
            lastRunOk     = ok,
            lastRunResult = if (ok) "Completed" else "Failed",
            runCount      = s.runCount + 1,
        ))
    }

    // ── Live execution state (called from Binder thread) ─────────────────────

    /**
     * Forge OS signals it has begun working on [scheduleId].
     * Adds a [RunningEntry] so the Schedules UI can immediately show "Running…".
     */
    fun markRunning(scheduleId: String, planSummary: String) {
        _runningSchedules.value =
            _runningSchedules.value.filterNot { it.scheduleId == scheduleId } +
            RunningEntry(scheduleId, planSummary)
    }

    /**
     * Forge OS signals it has finished [scheduleId].
     * Removes the running entry and persists the outcome so last-run labels
     * survive app restarts.
     */
    fun markCompleted(scheduleId: String, ok: Boolean, result: String) {
        _runningSchedules.value =
            _runningSchedules.value.filterNot { it.scheduleId == scheduleId }

        val s = _schedules.value.firstOrNull { it.id == scheduleId } ?: return
        replace(s.copy(
            lastRunAt     = System.currentTimeMillis(),
            lastRunOk     = ok,
            lastRunResult = result,
            runCount      = s.runCount + 1,
        ))
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun update(list: List<Schedule>) {
        _schedules.value = list
        prefs.edit().putString("schedules", gson.toJson(list)).apply()
    }

    private fun load(): List<Schedule> = runCatching {
        val json = prefs.getString("schedules", null) ?: return@runCatching emptyList()
        val type = object : TypeToken<List<Schedule>>() {}.type
        gson.fromJson<List<Schedule>>(json, type) ?: emptyList()
    }.getOrElse { emptyList() }

    // ── Seed data ─────────────────────────────────────────────────────────────

    fun seedIfEmpty() {
        if (_schedules.value.isNotEmpty()) return
        update(listOf(
            Schedule(
                name        = "Morning briefing",
                triggerType = TriggerType.TIME,
                timeHour    = 8, timeMinute = 0,
                repeatMode  = RepeatMode.WEEKDAYS,
                actionPlan  = "Open Gmail, read the 3 most recent unread emails aloud. Then open Calendar and read today's events.",
            ),
            Schedule(
                name        = "Battery check",
                triggerType = TriggerType.EVENT,
                eventTrigger = EventTrigger.BATTERY_LOW,
                actionPlan  = "Open Settings, go to Battery, enable Battery Saver mode.",
                isEnabled   = false,
            ),
        ))
    }
}
