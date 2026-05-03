package com.forge.autophone.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.forge.autophone.AutoPhoneApp
import com.forge.autophone.IAutoPhoneService

private const val TAG = "AutoPhoneBinderService"

/**
 * The AIDL endpoint that Forge OS binds to for all phone-control, notification,
 * and schedule lifecycle operations.
 *
 * ## Delegations
 * - Screen/gesture tools → [AutoPhoneAccessibilityService]
 * - Notification tools   → [AutoPhoneNotificationService] + [NotificationRepository]
 * - Schedule lifecycle   → [ScheduleRepository] (live-state flow)
 *
 * Callers must hold com.forge.autophone.permission.CONTROL (enforced in
 * AndroidManifest.xml via android:permission on this <service> tag).
 */
class AutoPhoneBinderService : Service() {

    private val container get() = (application as AutoPhoneApp).container

    private val binder = object : IAutoPhoneService.Stub() {

        private fun acc() = AutoPhoneAccessibilityService.instance
        private fun ntf() = AutoPhoneNotificationService.instance
        private fun noAcc() = """{"ok":false,"error":"Accessibility service not active"}"""
        private fun noNtf() = """{"ok":false,"error":"Notification listener not active — grant Notification Access in Settings"}"""

        // ── Screen-control tools ──────────────────────────────────────────────

        override fun readScreen()                  = acc()?.readScreen()           ?: noAcc()
        override fun tapByText(text: String)       = acc()?.tapByText(text)        ?: noAcc()
        override fun tapAt(x: Int, y: Int)         = acc()?.tapAt(x, y)            ?: noAcc()
        override fun typeText(text: String)        = acc()?.typeText(text)          ?: noAcc()
        override fun swipe(dir: String, px: Int)   = acc()?.swipe(dir, px)         ?: noAcc()
        override fun scroll(dir: String)           = acc()?.scroll(dir)             ?: noAcc()
        override fun launchApp(pkg: String)        = acc()?.launchApp(pkg)          ?: noAcc()
        override fun goBack()                      = acc()?.goBack()                ?: noAcc()
        override fun goHome()                      = acc()?.goHome()                ?: noAcc()
        override fun openNotifications()           = acc()?.openNotifications()     ?: noAcc()
        override fun screenshot()                  = acc()?.screenshot()            ?: noAcc()
        override fun findAndTap(text: String)      = acc()?.findAndTap(text)        ?: noAcc()
        override fun isServiceActive()             = acc() != null

        // ── Notification tools ────────────────────────────────────────────────

        override fun readNotifications(): String {
            ntf() ?: return noNtf()
            val json = container.notificationRepo.toJsonArray()
            return """{"ok":true,"output":$json}"""
        }

        override fun dismissNotification(key: String): String {
            val svc = ntf() ?: return noNtf()
            return if (svc.dismiss(key))
                """{"ok":true}"""
            else
                """{"ok":false,"error":"Notification not found or dismiss failed"}"""
        }

        override fun replyToNotification(key: String, text: String): String {
            val svc = ntf() ?: return noNtf()
            val notif = container.notificationRepo.find(key)
                ?: return """{"ok":false,"error":"Notification key not found"}"""
            if (!notif.canReply)
                return """{"ok":false,"error":"Notification does not support direct reply"}"""
            return if (svc.reply(key, text))
                """{"ok":true}"""
            else
                """{"ok":false,"error":"Reply action failed"}"""
        }

        override fun isNotificationListenerActive() = ntf() != null

        // ── Schedule lifecycle ────────────────────────────────────────────────

        override fun notifyScheduleStarted(scheduleId: String, planSummary: String) {
            Log.i(TAG, "Schedule started: $scheduleId — $planSummary")
            container.scheduleRepo.markRunning(scheduleId, planSummary)
        }

        override fun notifyScheduleCompleted(scheduleId: String, ok: Boolean, result: String) {
            Log.i(TAG, "Schedule completed: $scheduleId ok=$ok — $result")
            container.scheduleRepo.markCompleted(scheduleId, ok, result)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "Forge OS bound to AutoPhoneBinderService")
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Forge OS unbound")
        return super.onUnbind(intent)
    }
}
