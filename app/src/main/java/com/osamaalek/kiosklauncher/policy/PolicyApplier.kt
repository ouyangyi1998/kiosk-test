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
        dpm.setLockTaskFeatures(admin, features)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dpm.setStatusBarDisabled(admin, policy.disableStatusBar)
        }

        dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
    }

    private fun buildLockTaskFeatures(policy: KioskPolicy): Int {
        var flags = DevicePolicyManager.LOCK_TASK_FEATURE_NONE
        if (!policy.disableNotifications) {
            flags = flags or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
        }
        if (!policy.disableStatusBar) {
            flags = flags or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
        }
        return flags
    }
}
