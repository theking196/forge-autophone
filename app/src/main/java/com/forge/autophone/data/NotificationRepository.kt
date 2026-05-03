package com.forge.autophone.data

import android.app.Notification
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a single intercepted notification that AutoPhone has captured.
 *
 * @param key       Unique Android notification key (used to dismiss or reply).
 * @param pkg       Source package (e.g. "com.whatsapp").
 * @param appLabel  Human-readable app name (e.g. "WhatsApp").
 * @param title     Notification title text.
 * @param body      Notification body / big-text content.
 * @param postedAt  Unix ms timestamp when the notification was posted.
 * @param canReply  True if the notification has a direct-reply RemoteInput action.
 */
data class CapturedNotification(
    val key: String,
    val pkg: String,
    val appLabel: String,
    val title: String,
    val body: String,
    val postedAt: Long,
    val canReply: Boolean,
) {
    val timeLabel: String get() =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(postedAt))

    /** Compact JSON representation returned to Forge OS via readNotifications(). */
    fun toJson() = buildString {
        append("""{"key":${jsonStr(key)},"app":${jsonStr(appLabel)},"pkg":${jsonStr(pkg)},""")
        append(""""title":${jsonStr(title)},"body":${jsonStr(body)},""")
        append(""""postedAt":$postedAt,"canReply":$canReply}""")
    }

    private fun jsonStr(s: String) = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

/**
 * In-memory ring-buffer of intercepted notifications.
 *
 * [AutoPhoneNotificationService] (a [NotificationListenerService]) calls
 * [onNotificationPosted] and [onNotificationRemoved] as Android delivers them.
 * Both the Dashboard UI (live feed) and Forge OS (via [readNotifications] on
 * the AIDL binder) observe [notifications].
 *
 * Max capacity: [MAX] entries — oldest are evicted automatically.
 */
class NotificationRepository {

    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications

    fun onNotificationPosted(sbn: StatusBarNotification, appLabel: String) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val body  = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""

        val canReply = sbn.notification.actions?.any { action ->
            action.remoteInputs?.isNotEmpty() == true
        } ?: false

        val captured = CapturedNotification(
            key       = sbn.key,
            pkg       = sbn.packageName,
            appLabel  = appLabel,
            title     = title,
            body      = body,
            postedAt  = sbn.postTime,
            canReply  = canReply,
        )

        // Prepend new notification; evict oldest if over capacity
        val current = _notifications.value.filterNot { it.key == sbn.key }
        _notifications.value = (listOf(captured) + current).take(MAX)
    }

    fun onNotificationRemoved(key: String) {
        _notifications.value = _notifications.value.filterNot { it.key == key }
    }

    /**
     * Returns the full live list as a JSON array string for Forge OS.
     * Example: [{"key":"...","app":"WhatsApp",...}, ...]
     */
    fun toJsonArray(): String =
        "[${_notifications.value.joinToString(",") { it.toJson() }}]"

    /** Returns the notification with this key, or null if it is no longer present. */
    fun find(key: String): CapturedNotification? =
        _notifications.value.firstOrNull { it.key == key }

    companion object {
        private const val MAX = 50
    }
}
