package com.osamaalek.kiosklauncher.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.osamaalek.kiosklauncher.policy.KioskPolicy
import com.osamaalek.kiosklauncher.receivers.KioskScheduleReceiver
import com.osamaalek.kiosklauncher.util.TimeUtil

object KioskScheduler {
    private const val REQUEST_START = 1001
    private const val REQUEST_STOP = 1002
    private const val REQUEST_REBOOT = 1003

    fun scheduleAll(context: Context, policy: KioskPolicy) {
        cancelAll(context)
        scheduleAction(context, KioskScheduleReceiver.ACTION_START, policy.scheduleStart, REQUEST_START)
        scheduleAction(context, KioskScheduleReceiver.ACTION_STOP, policy.scheduleStop, REQUEST_STOP)
        scheduleAction(context, KioskScheduleReceiver.ACTION_REBOOT, policy.rebootTime, REQUEST_REBOOT)
    }

    fun cancelAll(context: Context) {
        cancel(context, KioskScheduleReceiver.ACTION_START, REQUEST_START)
        cancel(context, KioskScheduleReceiver.ACTION_STOP, REQUEST_STOP)
        cancel(context, KioskScheduleReceiver.ACTION_REBOOT, REQUEST_REBOOT)
    }

    fun scheduleNext(context: Context, action: String, time: String?) {
        val requestCode = when (action) {
            KioskScheduleReceiver.ACTION_START -> REQUEST_START
            KioskScheduleReceiver.ACTION_STOP -> REQUEST_STOP
            KioskScheduleReceiver.ACTION_REBOOT -> REQUEST_REBOOT
            else -> return
        }
        scheduleAction(context, action, time, requestCode)
    }

    private fun scheduleAction(context: Context, action: String, time: String?, requestCode: Int) {
        if (time.isNullOrBlank()) return
        val triggerAt = TimeUtil.nextTriggerAt(time) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, KioskScheduleReceiver::class.java).setAction(action)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    private fun cancel(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, KioskScheduleReceiver::class.java).setAction(action)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
