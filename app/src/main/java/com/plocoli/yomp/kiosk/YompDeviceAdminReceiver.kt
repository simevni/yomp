package com.plocoli.yomp.kiosk

import android.app.admin.DeviceAdminReceiver

/**
 * Device-admin receiver required to make Yomp a Device Owner.
 * No custom behavior is needed — its mere existence lets `dpm set-device-owner`
 * target this app, which in turn unlocks true kiosk (Lock Task) mode.
 */
class YompDeviceAdminReceiver : DeviceAdminReceiver()
