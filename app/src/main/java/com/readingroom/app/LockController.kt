package com.readingroom.app

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

enum class LockStrength { LOCKED_DEVICE, SCREEN_PINNING }

/** Turns Android's lock task mode on and off for the activity. */
class LockController(private val activity: Activity) {
    private val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(activity, AdminReceiver::class.java)

    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(activity.packageName)

    val strength: LockStrength
        get() = if (isDeviceOwner) LockStrength.LOCKED_DEVICE else LockStrength.SCREEN_PINNING

    val isLocked: Boolean
        get() {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        }

    fun engage() {
        if (isDeviceOwner) {
            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
            if (Build.VERSION.SDK_INT >= 28) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, home, ComponentName(activity, MainActivity::class.java))
        }
        if (!isLocked) runCatching { activity.startLockTask() }
    }

    fun release() {
        if (isLocked) runCatching { activity.stopLockTask() }
        if (isDeviceOwner) dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName)
    }
}
