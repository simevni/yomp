package com.plocoli.yomp.data

import android.content.Context

/**
 * Small parent-settings store (SharedPreferences): the parent PIN that gates the
 * settings/editor and kiosk exit, plus whether kiosk mode is currently armed.
 *
 * The PIN defaults to [DEFAULT_PIN] — change it in the in-app parent panel.
 * kioskEnabled defaults to true and is reset to true on every boot (see BootReceiver),
 * so unlocking is always temporary: a restart re-locks the device.
 */
object SettingsStore {
    private const val PREFS = "yomp_settings"
    private const val KEY_PIN = "parent_pin"
    private const val KEY_KIOSK = "kiosk_enabled"
    const val DEFAULT_PIN = "1234"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pin(context: Context): String =
        prefs(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setPin(context: Context, newPin: String) {
        prefs(context).edit().putString(KEY_PIN, newPin).apply()
    }

    fun checkPin(context: Context, input: String): Boolean = input == pin(context)

    fun kioskEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KIOSK, true)

    fun setKioskEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KIOSK, enabled).apply()
    }
}
