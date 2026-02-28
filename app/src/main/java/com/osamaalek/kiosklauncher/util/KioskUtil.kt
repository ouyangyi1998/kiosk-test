package com.osamaalek.kiosklauncher.util

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.osamaalek.kiosklauncher.MyDeviceAdminReceiver
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.policy.PolicyApplier
import com.osamaalek.kiosklauncher.policy.PolicyStore
import com.osamaalek.kiosklauncher.ui.MainActivity

class KioskUtil {
    companion object {
        private const val RUNTIME_PREFS = "kiosk_runtime"
        private const val KEY_MANUAL_EXIT = "manual_exit"

        fun resumeKioskMode(context: Context) {
            runtimePrefs(context).edit().putBoolean(KEY_MANUAL_EXIT, false).apply()
        }

        fun startKioskMode(context: Activity, launchedAsHome: Boolean = false) {
            if (runtimePrefs(context).getBoolean(KEY_MANUAL_EXIT, false)) {
                // When manually exited, only HOME launches should be redirected.
                if (launchedAsHome) {
                    openAlternativeHome(context)
                }
                return
            }
            val devicePolicyManager =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val myDeviceAdmin = ComponentName(context, MyDeviceAdminReceiver::class.java)
            val isAdminActive = devicePolicyManager.isAdminActive(myDeviceAdmin)
            val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(context.packageName)

            if (!isAdminActive) {
                openDeviceAdminSettings(context)
            }

            if (isDeviceOwner) {
                val filter = IntentFilter(Intent.ACTION_MAIN)
                filter.addCategory(Intent.CATEGORY_HOME)
                filter.addCategory(Intent.CATEGORY_DEFAULT)
                val activity = ComponentName(context, MainActivity::class.java)
                try {
                    devicePolicyManager.addPersistentPreferredActivity(myDeviceAdmin, filter, activity)
                } catch (_: SecurityException) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_missing_owner_permission),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val policy = PolicyStore(context).getPolicy()
                PolicyApplier(context).apply(policy)
                applyWindowPolicy(context, policy.hideNavigationBar)
            } else {
                Toast.makeText(
                    context, context.getString(R.string.toast_not_owner_device), Toast.LENGTH_SHORT
                ).show()
            }

            if (isAdminActive && isDeviceOwner) {
                try {
                    context.startLockTask()
                } catch (_: IllegalStateException) {
                    // LockTask may fail if not yet allowed by DPM, retry on next lifecycle.
                } catch (_: SecurityException) {
                    // Device may not allow lock task yet.
                }
            }
        }

        fun stopKioskMode(context: Activity) {
            val devicePolicyManager =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val myDeviceAdmin = ComponentName(context, MyDeviceAdminReceiver::class.java)
            runtimePrefs(context).edit().putBoolean(KEY_MANUAL_EXIT, true).apply()
            val alternativeHome = findAlternativeHome(context)
            if (devicePolicyManager.isAdminActive(myDeviceAdmin)) {
                try {
                    context.stopLockTask()
                } catch (_: IllegalStateException) {
                    // Not currently in lock task mode.
                }
            }
            if (devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                try {
                    devicePolicyManager.setLockTaskPackages(myDeviceAdmin, emptyArray())
                    devicePolicyManager.setLockTaskFeatures(
                        myDeviceAdmin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                    )
                } catch (_: Exception) {
                    // Ignore ROM-level lock task API inconsistencies.
                }
                try {
                    devicePolicyManager.clearPackagePersistentPreferredActivities(
                        myDeviceAdmin,
                        context.packageName
                    )
                } catch (_: SecurityException) {
                    // Ignore if owner privileges change at runtime.
                }

                if (alternativeHome != null) {
                    try {
                        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addCategory(Intent.CATEGORY_DEFAULT)
                        }
                        val target = ComponentName(
                            alternativeHome.activityInfo.packageName,
                            alternativeHome.activityInfo.name
                        )
                        devicePolicyManager.addPersistentPreferredActivity(myDeviceAdmin, filter, target)
                    } catch (_: SecurityException) {
                        // Will throw on Android 11+ for different package.
                    }
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        devicePolicyManager.setStatusBarDisabled(myDeviceAdmin, false)
                    }
                    devicePolicyManager.clearUserRestriction(
                        myDeviceAdmin,
                        android.os.UserManager.DISALLOW_UNINSTALL_APPS
                    )
                } catch (_: SecurityException) {
                    // Ignore if owner privileges change at runtime.
                }
            }
            context.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            openAlternativeHome(context)
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

        private fun openDeviceAdminSettings(context: Context) {
            val explicitIntent = Intent().setComponent(
                ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")
            )
            val fallbackIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
            try {
                context.startActivity(explicitIntent)
            } catch (_: Exception) {
                try {
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_cannot_open_admin_settings),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        private fun runtimePrefs(context: Context) =
            context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)

        private fun findAlternativeHome(context: Context): ResolveInfo? {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val allHomes = context.packageManager.queryIntentActivities(intent, 0)
            return allHomes.firstOrNull { it.activityInfo.packageName != context.packageName }
        }

        private fun openAlternativeHome(context: Context) {
            val target = findAlternativeHome(context)
            if (target != null) {
                try {
                    val intent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setComponent(ComponentName(target.activityInfo.packageName, target.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {
                    // Fallback below.
                }
            }
            try {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_no_alternative_home), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}