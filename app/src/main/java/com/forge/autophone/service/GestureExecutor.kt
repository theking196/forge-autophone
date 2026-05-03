package com.forge.autophone.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.content.Context

/**
 * Builds and dispatches [GestureDescription] gestures through the
 * active [AutoPhoneAccessibilityService].
 *
 * All methods return immediately; success/failure is reported via the
 * provided callback which is invoked on the main thread.
 */
object GestureExecutor {

    // ──────────────────────────────────────────────────────────────────────────
    // Tap
    // ──────────────────────────────────────────────────────────────────────────

    fun tap(service: AccessibilityService, x: Float, y: Float, cb: GestureCallback) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        dispatch(service, stroke, cb)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Swipe / scroll
    // ──────────────────────────────────────────────────────────────────────────

    fun swipe(
        service: AccessibilityService,
        direction: String,
        amount: Int,
        cb: GestureCallback,
    ) {
        val (w, h) = screenSize(service)
        val cx = w / 2f
        val cy = h / 2f
        val (startX, startY, endX, endY) = when (direction.lowercase()) {
            "up"    -> listOf(cx, cy + amount / 2f, cx, cy - amount / 2f)
            "down"  -> listOf(cx, cy - amount / 2f, cx, cy + amount / 2f)
            "left"  -> listOf(cx + amount / 2f, cy, cx - amount / 2f, cy)
            "right" -> listOf(cx - amount / 2f, cy, cx + amount / 2f, cy)
            else    -> listOf(cx, cy + amount / 2f, cx, cy - amount / 2f)
        }
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 400)
        dispatch(service, stroke, cb)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun dispatch(
        service: AccessibilityService,
        stroke: GestureDescription.StrokeDescription,
        cb: GestureCallback,
    ) {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) = cb.onSuccess()
            override fun onCancelled(gestureDescription: GestureDescription) =
                cb.onFailure("Gesture cancelled")
        }, null)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(service: AccessibilityService): Pair<Int, Int> {
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    interface GestureCallback {
        fun onSuccess()
        fun onFailure(reason: String)
    }
}
