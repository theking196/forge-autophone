package com.forge.autophone.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.forge.autophone.AutoPhoneApp

private const val TAG = "AutoPhoneNotifSvc"

/**
 * Android [NotificationListenerService] that intercepts every status-bar
 * notification posted on the device and forwards it to [NotificationRepository].
 *
 * **Setup required:** The user must grant Notification Access in
 * Settings → Notifications → Notification access → Forge AutoPhone.
 * This is separate from the Accessibility permission.
 *
 * ## Capabilities exposed to Forge OS via IAutoPhoneService
 *
 * | Method                          | What it does                              |
 * |---------------------------------|-------------------------------------------|
 * | readNotifications()             | Returns JSON array of live notifications  |
 * | dismissNotification(key)        | Cancels the notification by key           |
 * | replyToNotification(key, text)  | Fires a direct-reply RemoteInput action   |
 *
 * ## Privacy
 * Notifications can contain sensitive data (messages, emails, OTPs). AutoPhone
 * only forwards notification content to Forge OS — no third-party servers are
 * ever contacted.
 */
class AutoPhoneNotificationService : NotificationListenerService() {

    private val repo get() =
        (application as AutoPhoneApp).container.notificationRepo

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        instance = this
        // Replay currently-active notifications on first connect
        runCatching { activeNotifications?.forEach { sbn -> post(sbn) } }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Notification listener disconnected")
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        post(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        repo.onNotificationRemoved(sbn.key)
        Log.d(TAG, "Removed: ${sbn.key}")
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Dismiss the notification identified by [key].
     * Returns true if the notification was found and cancelled.
     */
    fun dismiss(key: String): Boolean {
        return runCatching {
            cancelNotification(key)
            true
        }.getOrElse { e ->
            Log.e(TAG, "dismiss failed for $key", e)
            false
        }
    }

    /**
     * Send a direct-reply to the notification identified by [key].
     *
     * Android requires us to find the first action that has a [RemoteInput]
     * and fire its [PendingIntent] with the typed reply bundled in.
     *
     * @return true on success, false if the notification or reply action is
     *         not found / the RemoteInput fires with an error.
     */
    fun reply(key: String, text: String): Boolean {
        val sbn = runCatching {
            activeNotifications?.firstOrNull { it.key == key }
        }.getOrNull() ?: return false

        val replyAction = sbn.notification.actions
            ?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
            ?: return false

        val remoteInput = replyAction.remoteInputs.first()
        val results = Bundle().apply {
            putCharSequence(remoteInput.resultKey, text)
        }
        val fillIn = Intent().also { RemoteInput.addResultsToIntent(replyAction.remoteInputs, it, results) }

        return runCatching {
            replyAction.actionIntent.send(applicationContext, 0, fillIn)
            true
        }.getOrElse { e ->
            Log.e(TAG, "reply failed for $key", e)
            false
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun post(sbn: StatusBarNotification) {
        // Skip system / AutoPhone's own notifications
        if (sbn.packageName == packageName) return
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, PackageManager.GET_META_DATA)
            ).toString()
        }.getOrElse { sbn.packageName }
        repo.onNotificationPosted(sbn, label)
        Log.d(TAG, "Posted: ${sbn.key} from $label")
    }

    // ── Singleton access ──────────────────────────────────────────────────────

    companion object {
        @Volatile var instance: AutoPhoneNotificationService? = null
    }
}
