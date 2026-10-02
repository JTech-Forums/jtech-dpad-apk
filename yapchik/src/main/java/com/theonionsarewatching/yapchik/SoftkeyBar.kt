package com.theonionsarewatching.yapchik

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.EnumMap

/**
 * The visual softkey bar: a slim strip pinned to the bottom of the screen with
 * LEFT and RIGHT labels. Yapchik injects and removes it for you — you
 * normally never construct this class yourself.
 *
 * Labels are also clickable, so hybrid devices (touch + keypad) can tap them.
 *
 * Modified for jtech-dpad-apk (alltechdev, 2026-09-30):
 * - a label-only CENTER label between LEFT and RIGHT, GONE while CENTER is unbound so
 *   two-slot screens lay out exactly as before;
 * - colors are re-applied from [Yapchik.style] on every [bind], so a style change
 *   (e.g. the host's light/dark theme flipping) repaints the live bar. Before, colors
 *   were read once at construction and the controller reuses its bar for the life of
 *   the Activity, so a repaint never landed.
 */
class SoftkeyBar(context: Context) : LinearLayout(context) {

    private val labels = EnumMap<SoftkeySlot, TextView>(SoftkeySlot::class.java)
    private var actions: Map<SoftkeySlot, SoftkeyAction> = emptyMap()
    private val divider: View

    init {
        val style = Yapchik.style
        orientation = VERTICAL
        // Force LTR so LEFT always means the physical left of the screen,
        // regardless of the app's locale direction.
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(style.backgroundColor)
        isClickable = true // swallow stray touches so they don't hit views underneath
        // ...but NEVER take D-pad focus. Since API 26 a clickable View with the default
        // focusable="auto" resolves to FOCUSABLE, so `isClickable = true` above quietly made the
        // whole bar a focus target: walking DOWN past the last control moved focus off the content
        // and painted the framework's focus plate across the entire bar (tester, 2026-07-20). The
        // bar is driven by the PHYSICAL soft keys and is not reachable or activatable by focus, so
        // a focus stop on it is a dead end the user has to blindly walk back out of.
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false

        // 1dp top hairline
        divider = View(context).apply { setBackgroundColor(style.dividerColor) }
        addView(divider, LayoutParams(LayoutParams.MATCH_PARENT, dp(1)))

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val slots = listOf(
            SoftkeySlot.LEFT to (Gravity.LEFT or Gravity.CENTER_VERTICAL),
            SoftkeySlot.CENTER to Gravity.CENTER,
            SoftkeySlot.RIGHT to (Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        )
        val hp = dp(style.horizontalPaddingDp)
        for ((slot, gravity) in slots) {
            val tv = TextView(context).apply {
                this.gravity = gravity
                setTextColor(textColorFor(slot))
                textSize = style.textSizeSp
                // Medium weight instead of fake bold — synthetic bold clips
                // label glyphs on some keypad-phone font stacks.
                if (style.bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(hp, 0, hp, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setOnClickListener { actions[slot]?.let { a -> flash(slot); a.onPress.run() } }
                setOnLongClickListener {
                    actions[slot]?.onLongPress?.let { l -> flash(slot); l.run(); true } ?: false
                }
            }
            labels[slot] = tv
            if (slot == SoftkeySlot.CENTER) tv.visibility = View.GONE
            row.addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    /**
     * (Re)binds the labels + actions. Called by the controller on every
     * refresh/invalidate, so dynamic labels and [labelOverrides]
     * (from `setLabel`) are re-resolved each time.
     */
    @JvmOverloads
    fun bind(
        newActions: Map<SoftkeySlot, SoftkeyAction>,
        labelOverrides: Map<SoftkeySlot, CharSequence> = emptyMap()
    ) {
        actions = newActions
        applyStyle()
        for ((slot, tv) in labels) {
            val action = newActions[slot]
            tv.text = if (action == null) "" else (labelOverrides[slot] ?: action.label)
            if (slot == SoftkeySlot.CENTER) {
                tv.visibility = if (action == null) View.GONE else View.VISIBLE
            }
        }
    }

    /** Re-read the colors from [Yapchik.style] (see the class note). */
    private fun applyStyle() {
        val style = Yapchik.style
        setBackgroundColor(style.backgroundColor)
        divider.setBackgroundColor(style.dividerColor)
        for ((slot, tv) in labels) tv.setTextColor(textColorFor(slot))
    }

    private fun textColorFor(slot: SoftkeySlot): Int {
        val style = Yapchik.style
        return if (slot == SoftkeySlot.CENTER) style.centerTextColor ?: style.textColor
        else style.textColor
    }

    /** Brief visual feedback when a softkey fires. */
    fun flash(slot: SoftkeySlot) {
        val tv = labels[slot] ?: return
        tv.setTextColor(Yapchik.style.pressedTextColor)
        tv.postDelayed({ tv.setTextColor(textColorFor(slot)) }, 150)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
