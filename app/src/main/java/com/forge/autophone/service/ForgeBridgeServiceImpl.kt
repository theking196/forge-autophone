package com.forge.autophone.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.forge.autophone.AutoPhoneApp
import com.forge.os.bridge.IForgeBridgeCallback
import com.forge.os.bridge.IForgeBridgeService
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ForgeBridgeServiceImpl"

/**
 * Forge AutoPhone — Forge Bridge implementation.
 *
 * Exposes all AutoPhone capabilities (accessibility control + notification
 * management) through the generic [IForgeBridgeService] protocol so that
 * Forge OS auto-discovers and binds to this service without any custom
 * hard-coded logic. AutoPhone is simply ONE bridge app among many.
 *
 * Any third-party app can follow this exact pattern:
 *  1. Copy IForgeBridgeService.aidl + IForgeBridgeCallback.aidl into their
 *     aidl/com/forge/os/bridge/ directory.
 *  2. Create a Service that extends IForgeBridgeService.Stub.
 *  3. Return a tool manifest JSON from getToolManifest().
 *  4. Handle dispatch() calls and return result strings.
 *  5. Export the service with action "com.forge.os.bridge.TOOL_PROVIDER".
 *
 * Forge OS will discover, bind, and make all declared tools available to
 * the agent automatically — zero Forge OS changes required.
 */
class ForgeBridgeServiceImpl : Service() {

    private var forgeCallback: IForgeBridgeCallback? = null

    private val acc: AutoPhoneAccessibilityService?
        get() = AutoPhoneAccessibilityService.instance

    private val notifSvc: AutoPhoneNotificationService?
        get() = AutoPhoneNotificationService.instance

    private val notifRepo get() =
        (application as AutoPhoneApp).container.notificationRepo

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "Forge Bridge service bound")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Forge Bridge service created")
    }

    override fun onDestroy() {
        Log.i(TAG, "Forge Bridge service destroying")
        runCatching { forgeCallback?.onBridgeDisconnecting("AutoPhone service stopped") }
        super.onDestroy()
    }

    // ── IForgeBridgeService.Stub ──────────────────────────────────────────────

    private val binder = object : IForgeBridgeService.Stub() {

        override fun getBridgeInfo(): String = JSONObject().apply {
            put("id",          "com.forge.autophone")
            put("name",        "Forge AutoPhone")
            put("version",     "1.0.0")
            put("description", "Autonomous Android phone control via Accessibility: tap, type, swipe, read screen, manage notifications")
        }.toString()

        override fun getToolManifest(): String {
            Log.d(TAG, "getToolManifest called")
            return buildManifestJson()
        }

        override fun dispatch(toolName: String, argsJson: String): String {
            Log.d(TAG, "dispatch called: $toolName with args: ${argsJson.take(100)}")
            val args = runCatching { JSONObject(argsJson ?: "{}") }.getOrElse { JSONObject() }
            return runCatching { routeTool(toolName, args) }
                .onFailure { e -> Log.e(TAG, "dispatch failed for $toolName", e) }
                .getOrElse { e -> err(e.message ?: "unknown error") }
        }

        override fun setCallback(callback: IForgeBridgeCallback?) {
            Log.d(TAG, "setCallback called")
            forgeCallback = callback
        }

        override fun isReady(): Boolean {
            val ready = acc != null || notifSvc != null
            Log.d(TAG, "isReady: $ready (acc=${acc != null}, notifSvc=${notifSvc != null})")
            return ready
        }
    }

    // ── Tool manifest ─────────────────────────────────────────────────────────

    private fun buildManifestJson(): String {
        val tools = JSONArray()

        fun p(type: String, desc: String, required: Boolean = false) = JSONObject().apply {
            put("type", type); put("description", desc); put("required", required)
        }
        fun tool(name: String, desc: String, params: JSONObject = JSONObject()) =
            JSONObject().apply { put("name", name); put("description", desc); put("params", params) }
            .also { tools.put(it) }

        tool("autophone_read_screen",
            "Read the current foreground app's UI tree via Android Accessibility. Returns a JSON array of visible elements with text, bounds, and clickability.")

        tool("autophone_tap_text",
            "Tap the first visible element whose text contains the given string.",
            JSONObject().put("text", p("string", "Visible text to tap", true)))

        tool("autophone_tap_xy",
            "Tap at absolute screen pixel coordinates.",
            JSONObject().put("x", p("integer", "X pixel", true)).put("y", p("integer", "Y pixel", true)))

        tool("autophone_find_and_tap",
            "Read screen and tap the first matching element in one step — more reliable than read + tap.",
            JSONObject().put("text", p("string", "Text to find and tap", true)))

        tool("autophone_type",
            "Type text into the currently focused input field.",
            JSONObject().put("text", p("string", "Text to insert", true)))

        tool("autophone_swipe",
            "Swipe in a direction by a pixel amount.",
            JSONObject().put("direction", p("string", "up, down, left, or right", true))
                        .put("amount", p("integer", "Pixels to swipe (e.g. 500)", true)))

        tool("autophone_scroll",
            "Scroll the focused scrollable container up or down.",
            JSONObject().put("direction", p("string", "up or down", true)))

        tool("autophone_launch_app",
            "Launch an app by package name (e.g. com.whatsapp) or display label (e.g. WhatsApp).",
            JSONObject().put("app", p("string", "Package name or display label", true)))

        tool("autophone_go_back",          "Press the system Back button.")
        tool("autophone_go_home",          "Press the system Home button.")
        tool("autophone_open_notifications","Pull down the notification shade.")
        tool("autophone_screenshot",       "Capture the current screen. Note: MediaProjection permission must be granted in AutoPhone Setup.")

        tool("autophone_status",
            "Check whether AutoPhone services (Accessibility + Notification access) are active.")

        tool("phone_notification_list",
            "List all current status-bar notifications (app, title, body, canReply). Requires Notification Access granted to AutoPhone.")

        tool("phone_notification_dismiss",
            "Dismiss a status-bar notification by its key from phone_notification_list.",
            JSONObject().put("key", p("string", "Notification key", true)))

        tool("phone_notification_reply",
            "Send a direct-reply to a notification. Only works when canReply=true (WhatsApp, Messages, Slack, etc.).",
            JSONObject().put("key",  p("string", "Notification key", true))
                        .put("text", p("string", "Reply text",       true)))

        return tools.toString()
    }

    // ── Tool routing ──────────────────────────────────────────────────────────

    private fun routeTool(name: String, args: JSONObject): String = when (name) {
        "autophone_read_screen"          -> acc?.readScreen()                                     ?: noAcc()
        "autophone_tap_text"             -> acc?.tapByText(args.getString("text"))                ?: noAcc()
        "autophone_tap_xy"               -> acc?.tapAt(args.getInt("x"), args.getInt("y"))        ?: noAcc()
        "autophone_find_and_tap"         -> acc?.findAndTap(args.getString("text"))               ?: noAcc()
        "autophone_type"                 -> acc?.typeText(args.getString("text"))                  ?: noAcc()
        "autophone_swipe"                -> acc?.swipe(args.getString("direction"), args.getInt("amount")) ?: noAcc()
        "autophone_scroll"               -> acc?.scroll(args.getString("direction"))               ?: noAcc()
        "autophone_launch_app"           -> acc?.launchApp(args.getString("app"))                  ?: noAcc()
        "autophone_go_back"              -> acc?.goBack()                                          ?: noAcc()
        "autophone_go_home"              -> acc?.goHome()                                          ?: noAcc()
        "autophone_open_notifications"   -> acc?.openNotifications()                               ?: noAcc()
        "autophone_screenshot"           -> acc?.screenshot()                                      ?: noAcc()
        "autophone_status"               -> buildStatus()

        "phone_notification_list" -> notifRepo.toJsonArray()

        "phone_notification_dismiss" -> {
            val key = args.getString("key")
            val ok  = notifSvc?.dismiss(key)
            when {
                notifSvc == null -> noNotifListener()
                ok == true       -> """{"ok":true,"key":"$key"}"""
                else             -> err("Notification not found or already dismissed (key=$key)")
            }
        }

        "phone_notification_reply" -> {
            val key  = args.getString("key")
            val text = args.getString("text")
            val ok   = notifSvc?.reply(key, text)
            when {
                notifSvc == null -> noNotifListener()
                ok == true       -> """{"ok":true,"key":"$key","replied":true}"""
                else             -> err("Reply failed — notification may not support direct reply (canReply=false) or was already dismissed")
            }
        }

        else -> err("Unknown tool: $name")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildStatus(): String {
        val accOk   = acc != null
        val notifOk = notifSvc != null
        return buildString {
            appendLine("{")
            appendLine("  \"bridge\": \"running\",")
            appendLine("  \"accessibility\": ${if (accOk) "true" else "false"},")
            appendLine("  \"notification_listener\": ${if (notifOk) "true" else "false"},")
            appendLine("  \"ready\": ${accOk || notifOk}")
            append("}")
        }
    }

    private fun noAcc()          = err("Accessibility service not enabled. Enable Forge AutoPhone in Settings → Accessibility.")
    private fun noNotifListener() = err("Notification listener not enabled. Grant Notification Access to Forge AutoPhone in Settings.")
    private fun ok(msg: String)  = """{"ok":true,"output":"$msg"}"""
    private fun err(msg: String) = """{"ok":false,"error":"${msg.replace("\"", "'")}"}"""
}
