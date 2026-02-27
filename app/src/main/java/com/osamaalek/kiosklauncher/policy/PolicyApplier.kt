package com.osamaalek.kiosklauncher.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.osamaalek.kiosklauncher.MyDeviceAdminReceiver

class PolicyApplier(private val context: Context) {
    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)

    fun apply(policy: KioskPolicy) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            return
        }

        val allowedPackages = policy.allowedPackages.toMutableSet().apply {
            add(context.packageName)
        }
        dpm.setLockTaskPackages(admin, allowedPackages.toTypedArray())

        val features = buildLockTaskFeatures(policy)
        try {
            dpm.setLockTaskFeatures(admin, features)
        } catch (_: IllegalArgumentException) {
            // Fallback for vendor ROMs with stricter lock task feature constraints.
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                dpm.setStatusBarDisabled(admin, policy.disableStatusBar)
            } catch (_: SecurityException) {
                // Ignore if ROM blocks status bar policy at runtime.
            }
        }

        dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
    }

    private fun buildLockTaskFeatures(policy: KioskPolicy): Int {
        var flags = DevicePolicyManager.LOCK_TASK_FEATURE_NONE
        val allowNotifications = !policy.disableNotifications
        val allowSystemInfo = !policy.disableStatusBar
        if (allowNotifications || allowSystemInfo) {
            flags = flags or DevicePolicyManager.LOCK_TASK_FEATURE_HOME
        }
        if (allowNotifications) {
            flags = flags or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
        }
        if (allowSystemInfo) {
            flags = flags or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
        }
        return flags
    }
}
