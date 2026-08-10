package com.plocoli.yomp.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.plocoli.yomp.data.SettingsStore

/**
 * Re-arms kiosk mode on every boot, so a parent's temporary unlock never survives
 * a restart. (The actual Lock Task is entered by MainActivity.onResume once the
 * home app launches on boot.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SettingsStore.setKioskEnabled(context, true)
        }
    }
}
