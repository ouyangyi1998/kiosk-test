package com.osamaalek.kiosklauncher.util

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.osamaalek.kiosklauncher.MyDeviceAdminReceiver
import com.osamaalek.kiosklauncher.policy.PolicyApplier
import com.osamaalek.kiosklauncher.policy.PolicyStore
import com.osamaalek.kiosklauncher.ui.MainActivity

class KioskUtil {
    companion object {
        fun startKioskMode(context: Activity) {
            val devicePolicyManager =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val myDeviceAdmin = ComponentName(context, MyDeviceAdminReceiver::class.java)
            val isAdminActive = devicePolicyManager.isAdminActive(myDeviceAdmin)
            val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(context.packageName)

            if (!isAdminActive) {
                context.startActivity(
                    Intent().setComponent(
                        ComponentName(
                            "com.android.settings", "com.android.settings.DeviceAdminSettings"
                        )
                    )
                )
            }

            if (isDeviceOwner) {
                val filter = IntentFilter(Intent.ACTION_MAIN)
                filter.addCategory(Intent.CATEGORY_HOME)
                filter.addCategory(Intent.CATEGORY_DEFAULT)
                val activity = ComponentName(context, MainActivity::class.java)
                devicePolicyManager.addPersistentPreferredActivity(myDeviceAdmin, filter, activity)

                val policy = PolicyStore(context).getPolicy()
                PolicyApplier(context).apply(policy)
                applyWindowPolicy(context, policy.hideNavigationBar)
            } else {
                Toast.makeText(
                    context, "This app is not an owner device", Toast.LENGTH_SHORT
                ).show()
            }

            if (isAdminActive) {
                try {
                    context.startLockTask()
                } catch (_: IllegalStateException) {
                    // LockTask may fail if not yet allowed by DPM, retry on next lifecycle.
                }
            }
        }

        fun stopKioskMode(context: Activity) {
            val devicePolicyManager =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val myDeviceAdmin = ComponentName(context, MyDeviceAdminReceiver::class.java)
            if (devicePolicyManager.isAdminActive(myDeviceAdmin)) {
                try {
                    context.stopLockTask()
                } catch (_: IllegalStateException) {
                    // Not currently in lock task mode.
                }
            }
            if (devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                devicePolicyManager.clearPackagePersistentPreferredActivities(
                    myDeviceAdmin,
                    context.packageName
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    devicePolicyManager.setStatusBarDisabled(myDeviceAdmin, false)
                }
                devicePolicyManager.clearUserRestriction(
                    myDeviceAdmin,
                    android.os.UserManager.DISALLOW_UNINSTALL_APPS
                )
            }
            context.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }

        fun applyWindowPolicy(activity: Activity, hideNavigationBar: Boolean) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!hideNavigationBar) {
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                return
            }
            val flags = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            activity.window.decorView.systemUiVisibility = flags
        }
    }
}