package com.osamaalek.kiosklauncher.receivers

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.osamaalek.kiosklauncher.MyDeviceAdminReceiver
import com.osamaalek.kiosklauncher.policy.PolicyStore
import com.osamaalek.kiosklauncher.scheduler.KioskScheduler
import com.osamaalek.kiosklauncher.ui.KioskControlActivity

class KioskScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val policy = PolicyStore(context).getPolicy()

        when (action) {
            ACTION_START, ACTION_STOP -> {
                val controlIntent = Intent(context, KioskControlActivity::class.java)
                    .setAction(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(controlIntent)
            }
            ACTION_REBOOT -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)
                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    try {
                        dpm.reboot(admin)
                    } catch (_: SecurityException) {
                        // Ignore if device owner privileges are unavailable at runtime.
                    }
                }
            }
        }

        val time = when (action) {
            ACTION_START -> policy.scheduleStart
            ACTION_STOP -> policy.scheduleStop
            ACTION_REBOOT -> policy.rebootTime
            else -> null
        }
        KioskScheduler.scheduleNext(context, action, time)
    }

    companion object {
        const val ACTION_START = "com.osamaalek.kiosklauncher.action.START_KIOSK"
        const val ACTION_STOP = "com.osamaalek.kiosklauncher.action.STOP_KIOSK"
        const val ACTION_REBOOT = "com.osamaalek.kiosklauncher.action.REBOOT_DEVICE"
    }
}
