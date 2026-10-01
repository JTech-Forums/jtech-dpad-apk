package com.theonionsarewatching.yapchik

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * A short message that stays clear of the softkey bar.
 *
 * Added for jtech-dpad-apk (alltechdev, 2026-09-30): a framework Toast is drawn
 * at the system's default spot near the bottom of the screen, which on a small
 * keypad phone lands on top of the softkey bar and hides its labels, and since
 * targetSdk 30 a text Toast's position can't be changed. So while a bar is shown
 * the message is drawn in the Activity itself, just above the bar. With no bar
 * it is a plain Toast, exactly as before.
 */
object SoftkeyMessage {

    private const val TAG = "yapchik-message"

    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity, text: CharSequence, long: Boolean = false) {
        val barPx = Yapchik.controllerOrNull(activity)?.shownBarHeightPx ?: 0
        val content: ViewGroup? = activity.findViewById(android.R.id.content)
        if (barPx <= 0 || content !is FrameLayout) {
            Toast.makeText(activity, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
                .show()
            return
        }
        content.findViewWithTag<View>(TAG)?.let { content.removeView(it) }

        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val view = TextView(activity).apply {
            tag = TAG
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(0xEE2B2B2B.toInt())
                cornerRadius = dp(16).toFloat()
            }
            // Never a focus target: the D-pad and soft keys keep working under it.
            isFocusable = false
            isClickable = false
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = barPx + dp(8)
            leftMargin = dp(12)
            rightMargin = dp(12)
        }
        content.addView(view, lp)
        view.postDelayed(
            { (view.parent as? ViewGroup)?.removeView(view) },
            if (long) 3500L else 2000L
        )
    }
}
