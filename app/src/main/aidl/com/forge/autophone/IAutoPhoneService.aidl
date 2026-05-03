package com.forge.autophone;

/**
 * Forge AutoPhone — AIDL service interface.
 *
 * Forge OS (or any privileged caller holding com.forge.autophone.permission.CONTROL)
 * binds to this service to issue phone-control commands. The AccessibilityService
 * runs in the same process and executes each command synchronously on the
 * accessibility thread pool.
 *
 * All tool methods return String — a JSON envelope:
 *   {"ok":true,"output":"..."}   on success
 *   {"ok":false,"error":"..."}   on failure
 *
 * Notification methods let Forge OS read, dismiss, and reply to any
 * status-bar notification — extending agent awareness beyond what is
 * visible on screen.
 *
 * Schedule lifecycle methods let Forge OS push real-time execution status
 * back to the AutoPhone UI so users see a live "Running…" indicator.
 */
interface IAutoPhoneService {

    // ── Screen-control tools ─────────────────────────────────────────────────

    /** Read current screen UI tree → JSON element list */
    String readScreen();

    /** Tap element by visible text (exact or partial match) */
    String tapByText(String text);

    /** Tap at absolute screen coordinates */
    String tapAt(int x, int y);

    /** Type text into the currently focused input field */
    String typeText(String text);

    /** Swipe in direction ("up"|"down"|"left"|"right") by pixel amount */
    String swipe(String direction, int amount);

    /** Scroll in direction ("up"|"down") in the focused scrollable */
    String scroll(String direction);

    /** Launch an app by package name (e.g. "com.instagram.android") or display label */
    String launchApp(String packageOrLabel);

    /** Press the system Back button */
    String goBack();

    /** Press the system Home button */
    String goHome();

    /** Pull down the notification shade */
    String openNotifications();

    /** Capture screen as base64 PNG (requires MediaProjection grant) */
    String screenshot();

    /** Find element containing text and tap it — combines readScreen + tapByText */
    String findAndTap(String text);

    /** Returns true if AccessibilityService is connected and active */
    boolean isServiceActive();

    // ── Notification tools ───────────────────────────────────────────────────

    /**
     * Returns a JSON array of all currently-visible status-bar notifications
     * that AutoPhone has intercepted via NotificationListenerService.
     *
     * Each element:
     * {"key":"...","app":"WhatsApp","pkg":"com.whatsapp",
     *  "title":"Alice","body":"Hey, are you free?",
     *  "postedAt":1714727400000,"canReply":true}
     *
     * Requires Notification Access permission granted by the user.
     * Returns {"ok":false,"error":"Notification listener not active"} if
     * the user has not granted access.
     */
    String readNotifications();

    /**
     * Dismiss the notification identified by [key] (from readNotifications).
     * Returns {"ok":true} or {"ok":false,"error":"..."}.
     */
    String dismissNotification(String key);

    /**
     * Send a direct-reply to the notification identified by [key].
     * Only works when the notification's canReply field is true.
     * Returns {"ok":true} or {"ok":false,"error":"..."}.
     */
    String replyToNotification(String key, String text);

    /**
     * Returns true if the NotificationListenerService is active and
     * Notification Access has been granted by the user.
     */
    boolean isNotificationListenerActive();

    // ── Schedule lifecycle callbacks (Forge OS → AutoPhone) ───────────────────

    /**
     * Called by Forge OS the moment it begins executing a schedule's action plan.
     * AutoPhone shows a live "Running…" indicator on that schedule card.
     */
    oneway void notifyScheduleStarted(String scheduleId, String planSummary);

    /**
     * Called by Forge OS when it finishes (or gives up on) a schedule's plan.
     * AutoPhone clears the indicator and persists last-run status.
     */
    oneway void notifyScheduleCompleted(String scheduleId, boolean ok, String result);
}
