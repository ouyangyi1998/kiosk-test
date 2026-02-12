package com.osamaalek.kiosklauncher.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.osamaalek.kiosklauncher.policy.PolicyStore
import com.osamaalek.kiosklauncher.scheduler.KioskScheduler
import com.osamaalek.kiosklauncher.workers.PolicySyncWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val policy = PolicyStore(context).getPolicy()
        KioskScheduler.scheduleAll(context, policy)
        PolicySyncWorker.scheduleIfNeeded(context, policy)
    }
}
