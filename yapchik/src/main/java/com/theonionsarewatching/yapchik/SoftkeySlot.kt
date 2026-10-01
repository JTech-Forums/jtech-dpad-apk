package com.theonionsarewatching.yapchik

/**
 * The softkey positions on a keypad phone.
 *
 * Modified for jtech-dpad-apk (alltechdev, 2026-09-30): added [CENTER]. CENTER is
 * LABEL-ONLY - no [KeyProfile] ever maps a keycode to it, so the OK / D-pad-center key
 * keeps its native behavior; the slot only shows what OK does right now (and is
 * tappable on touch, like the other labels).
 */
enum class SoftkeySlot { LEFT, CENTER, RIGHT }
