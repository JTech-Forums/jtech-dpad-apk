package com.android.cts.jtech

// The app's thin integration layer over the vendored Yapchik softkey engine (:yapchik, LGPL-3.0),
// ported from vela-dpad's VelaSoftkeys. Keypad / kosher phones carry two hardware SOFT keys with
// no touch equivalent. The page (dumbcourse at /dumb) already decides what they do on every screen
// and layer and draws its own label bar; on a keypad device this native bar takes over: it shows
// the page's own left / center / right labels, and its physical keys press the page's own soft-key
// buttons. Nothing about the labels or actions is re-implemented here - MainActivity mirrors them.

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.InputDevice
import com.theonionsarewatching.yapchik.SoftkeyMode
import com.theonionsarewatching.yapchik.SoftkeyProfileChooser
import com.theonionsarewatching.yapchik.Softkeys
import com.theonionsarewatching.yapchik.Yapchik
import java.util.Locale

object JtechSoftkeys {

    /** The engine needs API 26 (see yapchik/build.gradle). Below that it is never installed and
     * the page keeps drawing its own soft-key bar, exactly as before. */
    @JvmStatic
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /** Whether the native bar is resolved-active (mode ON, or AUTO on a keypad device). While true,
     * the page's own bar is hidden so there is exactly one. */
    @JvmStatic
    fun isActive(): Boolean = isSupported && Yapchik.isActive

    /** One-time engine setup, called from [JtechApp.onCreate]. */
    @JvmStatic
    fun init(app: Application) {
        if (!isSupported) return
        Yapchik.install(app)
        // Softkeys are strictly a KEYPAD / D-pad-first feature. Gate the engine on the same
        // conservative detector Vela uses (plus a `jtech_force_dpad` test override) so the bar
        // NEVER appears on a touch phone in AUTO - touch phones keep the page's own bar.
        Yapchik.autoDetector = { ctx -> isDpadFirstDevice(ctx) }
        // Reserve bar space by padding the content view while the bar is up, rather than letting
        // it overlay the bottom of the page.
        Yapchik.autoInsetContent = true
        // Disable Yapchik's nav-bar GUARD. The window theme is a FRAMEWORK theme
        // (Theme.DeviceDefault), so with a nav-bar-hide policy Yapchik would grow the bar by the
        // device's probable navbar height. The bar is already reserved via autoInsetContent, so
        // that guard is phantom height - the bar looks DOUBLE-tall on a device with no real nav
        // bar (Vela tester report). 0 = off.
        Yapchik.navGuardDp = 0
        Yapchik.style.apply {
            heightDp = 44
            textSizeSp = 15f
            bold = true
        }
        applyThemeColors(light = false)
    }

    /** Settings: the page's "Soft-key bar" preference drives the mode. Small screens (auto) ->
     * AUTO, Always (on) -> ON, Never (off) -> OFF. Persisted by the engine; a no-op when unchanged. */
    @JvmStatic
    fun setModeFromPage(pref: String?) {
        if (!isSupported) return
        Yapchik.mode = when (pref) {
            "on" -> SoftkeyMode.ON
            "off" -> SoftkeyMode.OFF
            else -> SoftkeyMode.AUTO
        }
    }

    /** Run the engine's press-your-keys calibration, for phones whose soft keys emit
     * non-standard keycodes. Capture is driven by the key press itself. */
    @JvmStatic
    fun calibrate(activity: Activity) {
        if (isSupported) SoftkeyProfileChooser.startCalibration(activity)
    }

    @JvmStatic
    fun addStateListener(listener: Yapchik.StateListener) {
        if (isSupported) Yapchik.addStateListener(listener)
    }

    @JvmStatic
    fun removeStateListener(listener: Yapchik.StateListener) {
        if (isSupported) Yapchik.removeStateListener(listener)
    }

    /** A soft-key press, by the page's own slot name: "left", "center" or "right". */
    fun interface PressHandler {
        fun onPress(which: String)
    }

    /**
     * Mirror the page's soft-key bar. Each label is exactly what the page shows; an empty label
     * leaves that slot unbound (its key passes through to the page), and all three empty shows
     * no bar. CENTER is label-only in the engine - OK keeps reaching the page natively - so its
     * action only runs when the label is tapped. The bar repaints in place on a theme flip.
     */
    @JvmStatic
    fun bind(
        activity: Activity,
        leftLabel: String,
        centerLabel: String,
        rightLabel: String,
        light: Boolean,
        press: PressHandler
    ) {
        if (!isSupported) return
        applyThemeColors(light)
        val ctl = Softkeys.of(activity)
        if (leftLabel.isEmpty() && centerLabel.isEmpty() && rightLabel.isEmpty()) {
            ctl.clear()
            return
        }
        ctl.set {
            noDefaults()
            if (leftLabel.isNotEmpty()) left(leftLabel) { press.onPress("left") }
            // The page uppercases the centre label in CSS; match it.
            if (centerLabel.isNotEmpty()) {
                center(centerLabel.uppercase(Locale.getDefault())) { press.onPress("center") }
            }
            if (rightLabel.isNotEmpty()) right(rightLabel) { press.onPress("right") }
        }
    }

    /** Remove the bar (a page other than the forum app, or the page is reloading). */
    @JvmStatic
    fun clear(activity: Activity) {
        if (isSupported) Softkeys.of(activity).clear()
    }

    /** Paint the bar from the page's own theme tokens (dumbcourse 00-tokens.css), the colours its
     * bar uses: --surface background, --line hairline, --fg2 side labels, --fg centre label,
     * --accent press flash. */
    private fun applyThemeColors(light: Boolean) {
        Yapchik.style.apply {
            if (light) {
                backgroundColor = 0xFFFFFFFF.toInt()
                dividerColor = 0xFFDDE2EA.toInt()
                textColor = 0xFF525C6C.toInt()
                centerTextColor = 0xFF151920.toInt()
                pressedTextColor = 0xFF1C6BD6.toInt()
            } else {
                backgroundColor = 0xFF1A1D23.toInt()
                dividerColor = 0xFF2F343E.toInt()
                textColor = 0xFFA9B1BE.toInt()
                centerTextColor = 0xFFECEFF4.toInt()
                pressedTextColor = 0xFF5EA8FF.toInt()
            }
        }
    }

    /**
     * D-pad-FIRST detection, ported from vela-dpad's detectDpadFirst - deliberately CONSERVATIVE.
     * A device is D-pad-first only when it genuinely has NO touchscreen, or a PHYSICAL
     * (non-virtual) input device reports SOURCE_DPAD. The framework's "Virtual" aggregate device
     * reports DPAD on essentially every phone, so it is skipped; hybrid touch+keypad phones that
     * fail this test keep the page's own bar.
     */
    @JvmStatic
    fun isDpadFirstDevice(context: Context): Boolean {
        // Test override: `adb shell settings put global jtech_force_dpad 1`. Reading a Global
        // setting needs no permission; only adb / WRITE_SECURE_SETTINGS can set it.
        val forced = runCatching {
            android.provider.Settings.Global.getInt(context.contentResolver, "jtech_force_dpad", 0) == 1
        }.getOrDefault(false)
        if (forced) return true
        val noTouch = !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val hasPhysicalDpad = runCatching {
            InputDevice.getDeviceIds().any { id ->
                val dev = InputDevice.getDevice(id) ?: return@any false
                val virtual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) dev.isVirtual else id < 0
                !virtual && (dev.sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
            }
        }.getOrDefault(false)
        return noTouch || hasPhysicalDpad
    }
}
