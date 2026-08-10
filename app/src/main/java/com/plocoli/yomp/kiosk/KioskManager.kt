package com.plocoli.yomp.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * Drives kiosk (Lock Task) mode.
 *
 * When Yomp is the device's Device Owner (provisioned once with
 * `adb shell dpm set-device-owner com.plocoli.yomp/.kiosk.YompDeviceAdminReceiver`),
 * it can allow-list itself and enter Lock Task without any confirmation prompt —
 * blocking the notification shade, quick settings, recents, home, and every other app.
 * The only way out is our in-app parent PIN, which calls [exitKiosk].
 *
 * If Yomp is NOT the Device Owner, every call here is a safe no-op, so the app
 * still runs normally as an ordinary (unlocked) home app.
 */
object KioskManager {

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(context: Context) =
        ComponentName(context.applicationContext, YompDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context).isDeviceOwnerApp(context.packageName)

    /**
     * As Device Owner: allow-list Yomp (and the Spotify app, which App Remote binds to)
     * and lock down every Lock Task feature so nothing but Yomp is reachable.
     */
    fun configureIfOwner(context: Context) {
        val dpm = dpm(context)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val admin = admin(context)
        dpm.setLockTaskPackages(admin, arrayOf(context.packageName, "com.spotify.music"))
        // Lock down every Lock Task feature (nothing but Yomp reachable). API 28+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }
    }

    /** Enter kiosk mode if permitted. Safe to call repeatedly (no-op once locked). */
    fun enterKiosk(activity: Activity) {
        if (dpm(activity).isLockTaskPermitted(activity.packageName)) {
            activity.startLockTask()
        }
    }

    /** Leave kiosk mode (parent action). No-op if not currently locked. */
    fun exitKiosk(activity: Activity) {
        runCatching { activity.stopLockTask() }
    }

    /**
     * Relinquishes Device Owner entirely (escape hatch, behind the parent PIN).
     * After this, kiosk mode is no longer available and Yomp becomes an ordinary app —
     * so no factory reset is needed to undo the kiosk setup.
     */
    fun clearDeviceOwner(context: Context) {
        val dpm = dpm(context)
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            @Suppress("DEPRECATION")
            runCatching { dpm.clearDeviceOwnerApp(context.packageName) }
        }
    }
}
