package com.osamaalek.kiosklauncher.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.osamaalek.kiosklauncher.policy.PolicyApplier
import com.osamaalek.kiosklauncher.policy.PolicyStore
import com.osamaalek.kiosklauncher.scheduler.KioskScheduler
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class PolicySyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val store = PolicyStore(applicationContext)
        val policy = store.getPolicy()
        val url = policy.remoteUrl
        if (url.isNullOrBlank()) {
            return Result.success()
        }
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (!policy.remoteToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${policy.remoteToken}")
                }
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
                if (code in 200..299) {
                    try {
                        val merged = store.updateFromRemote(body)
                        PolicyApplier(applicationContext).apply(merged)
                        KioskScheduler.scheduleAll(applicationContext, merged)
                        Result.success()
                    } catch (_: Exception) {
                        Result.failure()
                    }
                } else if (code >= 500) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: java.io.IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "policy_sync_work"

        fun scheduleIfNeeded(context: Context, policy: com.osamaalek.kiosklauncher.policy.KioskPolicy) {
            if (policy.remoteUrl.isNullOrBlank()) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PolicySyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<PolicySyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME.once",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
