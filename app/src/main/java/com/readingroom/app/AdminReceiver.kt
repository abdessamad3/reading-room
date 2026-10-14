package com.readingroom.app

import android.app.admin.DeviceAdminReceiver

/**
 * Receiver that lets the app become Device Owner (set once from a computer with adb).
 * A Device Owner can stay in lock task mode without the system "screen pinned" prompt
 * and can hide the status bar, notifications and the Home and Recents buttons.
 */
class AdminReceiver : DeviceAdminReceiver()
