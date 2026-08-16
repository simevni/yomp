package com.plocoli.yomp.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class BatteryStatus(val percent: Int, val isCharging: Boolean)

/**
 * Battery level as a flow.
 *
 * Kiosk (Lock Task) mode hides the status bar, so the device's own battery indicator is
 * unreachable — Yomp has to show it itself or nobody can tell the tablet is about to die.
 *
 * ACTION_BATTERY_CHANGED is sticky: registering returns the current value immediately,
 * so there is no "unknown" gap on first composition.
 */
fun batteryStatusFlow(context: Context): Flow<BatteryStatus> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            parseBatteryStatus(intent)?.let { trySend(it) }
        }
    }
    val sticky = ContextCompat.registerReceiver(
        context.applicationContext,
        receiver,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    parseBatteryStatus(sticky)?.let { trySend(it) }

    awaitClose { runCatching { context.applicationContext.unregisterReceiver(receiver) } }
}.distinctUntilChanged()

private fun parseBatteryStatus(intent: Intent?): BatteryStatus? {
    if (intent == null) return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val percent = batteryPercent(level, scale) ?: return null
    val state = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return BatteryStatus(
        percent = percent,
        isCharging = state == BatteryManager.BATTERY_STATUS_CHARGING ||
            state == BatteryManager.BATTERY_STATUS_FULL,
    )
}

/**
 * Level/scale → 0..100, or null when the values are missing.
 * Scale is not always 100 (some devices report 1000), so it must not be assumed.
 */
fun batteryPercent(level: Int, scale: Int): Int? {
    if (level < 0 || scale <= 0) return null
    return (level * 100 / scale).coerceIn(0, 100)
}
