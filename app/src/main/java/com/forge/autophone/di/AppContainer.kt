package com.forge.autophone.di

import android.content.Context
import com.forge.autophone.data.ActionLogRepository
import com.forge.autophone.data.ForgeOsConnection
import com.forge.autophone.data.NotificationRepository
import com.forge.autophone.data.ScheduleRepository

/**
 * Manual DI container created once in [AutoPhoneApp].
 *
 * Dependency graph:
 *
 *   [forgeOs]          — AIDL binder to Forge OS (status / version queries)
 *   [actionLog]        — in-memory ring-buffer of every tool execution
 *   [scheduleRepo]     — persisted automation schedules (SharedPreferences + Gson)
 *   [notificationRepo] — in-memory ring-buffer of intercepted status-bar notifications
 *
 * Android system services (AccessibilityService, NotificationListenerService)
 * are singletons managed by Android; access them via their companion .instance.
 */
class AppContainer(context: Context) {
    val forgeOs: ForgeOsConnection           by lazy { ForgeOsConnection(context) }
    val actionLog: ActionLogRepository       by lazy { ActionLogRepository() }
    val scheduleRepo: ScheduleRepository     by lazy {
        ScheduleRepository(context).also { it.seedIfEmpty() }
    }
    val notificationRepo: NotificationRepository by lazy { NotificationRepository() }
}
