package com.forge.autophone.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.forge.autophone.AutoPhoneApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "AutoPhoneAS"

/**
 * The core phone-control engine for Forge AutoPhone.
 *
 * This AccessibilityService runs as a persistent background service once the
 * user enables it in Android Settings → Accessibility → Forge AutoPhone.
 *
 * External callers (Forge OS via [AutoPhoneBinderService], or [ToolTesterFragment]
 * for manual testing) call the synchronous helper methods below. Each method:
 *   1. Performs the accessibility action
 *   2. Records the result in [ActionLogRepository]
 *   3. Returns a JSON result string: `{"ok":true,"output":"..."}` or `{"ok":false,"error":"..."}`
 *
 * A static [instance] is kept so [AutoPhoneBinderService] can reach the live service
 * without re-binding. This is safe because both live in the same process.
 */
class AutoPhoneAccessibilityService : AccessibilityService() {

    companion object {
        private val _instance = MutableStateFlow<AutoPhoneAccessibilityService?>(null)
        val instanceFlow: StateFlow<AutoPhoneAccessibilityService?> = _instance
        val instance get() = _instance.value
    }

    private var lastPackage: CharSequence? = null
    private var lastWindowTitle: String? = null

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance.value = this
        Log.i(TAG, "AccessibilityService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _instance.value = null
        Log.i(TAG, "AccessibilityService disconnected")
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != null) lastPackage = event.packageName
        if (event.text.isNotEmpty()) lastWindowTitle = event.text.firstOrNull()?.toString()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public tool API — called by AutoPhoneBinderService / ToolTesterFragment
    // ──────────────────────────────────────────────────────────────────────────

    fun readScreen(): String = timed("autophone_read_screen", "") {
        val root = rootInActiveWindow
        ScreenReader.read(root, lastPackage, lastWindowTitle).also { root?.recycle() }
    }

    fun tapByText(text: String): String = timed("autophone_find_and_tap", text) {
        val root = rootInActiveWindow ?: return@timed err("No active window")
        val node = findNodeByText(root, text)
        root.recycle()
        if (node == null) return@timed err("Element not found: \"$text\"")
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        node.recycle()
        tapAt(bounds.centerX().toFloat(), bounds.centerY().toFloat(), "autophone_find_and_tap", text)
    }

    fun tapAt(x: Int, y: Int): String = tapAt(x.toFloat(), y.toFloat(), "autophone_tap", "$x,$y")

    private fun tapAt(x: Float, y: Float, tool: String, args: String): String {
        val latch = CountDownLatch(1)
        var result = err("Tap timed out")
        GestureExecutor.tap(this, x, y, object : GestureExecutor.GestureCallback {
            override fun onSuccess() { result = ok("tapped at (${x.toInt()}, ${y.toInt()})"); latch.countDown() }
            override fun onFailure(reason: String) { result = err(reason); latch.countDown() }
        })
        latch.await(2, TimeUnit.SECONDS)
        logResult(tool, args, result)
        return result
    }

    fun typeText(text: String): String = timed("autophone_type", "\"$text\"") {
        val root = rootInActiveWindow ?: return@timed err("No active window")
        val focused = findFocusedEditable(root)
        root.recycle()
        if (focused != null) {
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
            if (success) ok("typed ${text.length} chars") else err("ACTION_SET_TEXT failed — is field focused?")
        } else {
            err("No editable field focused")
        }
    }

    fun swipe(direction: String, amount: Int): String = timed("autophone_swipe", "$direction $amount") {
        val latch = CountDownLatch(1)
        var result = err("Swipe timed out")
        GestureExecutor.swipe(this, direction, amount, object : GestureExecutor.GestureCallback {
            override fun onSuccess() { result = ok("swiped $direction ${amount}px"); latch.countDown() }
            override fun onFailure(r: String) { result = err(r); latch.countDown() }
        })
        latch.await(2, TimeUnit.SECONDS)
        result
    }

    fun scroll(direction: String): String = swipe(direction, 600)

    fun launchApp(packageOrLabel: String): String = timed("autophone_launch_app", packageOrLabel) {
        val pm = packageManager
        // Try as package name first
        val intent = pm.getLaunchIntentForPackage(packageOrLabel)
            ?: pm.getInstalledApplications(0)
                   .firstOrNull { pm.getApplicationLabel(it).toString().equals(packageOrLabel, ignoreCase = true) }
                   ?.let { pm.getLaunchIntentForPackage(it.packageName) }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            ok("launched $packageOrLabel")
        } else {
            err("App not found: $packageOrLabel")
        }
    }

    fun goBack(): String = timed("autophone_go_back", "") {
        val ok = performGlobalAction(GLOBAL_ACTION_BACK)
        if (ok) ok("BACK dispatched") else err("GLOBAL_ACTION_BACK failed")
    }

    fun goHome(): String = timed("autophone_go_home", "") {
        val ok = performGlobalAction(GLOBAL_ACTION_HOME)
        if (ok) ok("HOME dispatched") else err("GLOBAL_ACTION_HOME failed")
    }

    fun openNotifications(): String = timed("autophone_open_notifs", "") {
        val ok = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        if (ok) ok("notifications shade opened") else err("GLOBAL_ACTION_NOTIFICATIONS failed")
    }

    fun screenshot(): String = timed("autophone_screenshot", "") {
        err("MediaProjection not configured — see Setup screen to grant screenshot permission")
    }

    fun findAndTap(text: String): String = tapByText(text)

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull { it.isClickable }
            ?: nodes.firstOrNull()
    }

    private fun findFocusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused?.isEditable == true) return focused
        // Walk tree for first editable
        return findEditableInTree(root)
    }

    private fun findEditableInTree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableInTree(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    /** Run [block], measure time, log to ActionLog, return JSON result. */
    private inline fun timed(tool: String, args: String, block: () -> String): String {
        val start = System.currentTimeMillis()
        val result = runCatching(block).getOrElse { err(it.message ?: "exception") }
        val ms = System.currentTimeMillis() - start
        logResult(tool, args, result, ms)
        return result
    }

    private fun logResult(tool: String, args: String, result: String, ms: Long = 0) {
        val ok = result.contains("\"ok\":true")
        val output = runCatching {
            com.google.gson.JsonParser.parseString(result).asJsonObject
                .get(if (ok) "output" else "error")?.asString ?: result
        }.getOrElse { result }
        (application as AutoPhoneApp).container.actionLog.record(tool, args, ok, output, ms)
    }

    private fun ok(output: String)  = """{"ok":true,"output":${com.google.gson.JsonPrimitive(output)}}"""
    private fun err(error: String)  = """{"ok":false,"error":${com.google.gson.JsonPrimitive(error)}}"""
}
