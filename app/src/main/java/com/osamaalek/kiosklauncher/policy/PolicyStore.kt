package com.osamaalek.kiosklauncher.policy

import android.content.Context

class PolicyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPolicy(): KioskPolicy {
        val json = prefs.getString(KEY_POLICY_JSON, null)
        if (json.isNullOrBlank()) {
            return defaultPolicy()
        }
        return try {
            val update = PolicyJson.parseUpdate(json)
            update.applyTo(defaultPolicy())
        } catch (_: Exception) {
            defaultPolicy()
        }
    }

    fun savePolicy(policy: KioskPolicy) {
        prefs.edit().putString(KEY_POLICY_JSON, PolicyJson.toJson(policy)).apply()
    }

    fun updateFromJson(json: String): KioskPolicy {
        val existing = getPolicy()
        val update = PolicyJson.parseUpdate(json)
        val merged = update.applyTo(existing)
        savePolicy(merged)
        return merged
    }

    fun updateFromRemote(json: String): KioskPolicy {
        val merged = updateFromJson(json)
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
        return merged
    }

    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    private fun defaultPolicy(): KioskPolicy {
        return KioskPolicy(
            kioskUrl = DEFAULT_KIOSK_URL,
            allowedPackages = emptySet(),
            disableStatusBar = true,
            disableNotifications = true,
            hideNavigationBar = true,
            exitPinHash = null,
            scheduleStart = null,
            scheduleStop = null,
            rebootTime = null,
            remoteUrl = null,
            remoteToken = null
        )
    }

    companion object {
        private const val PREFS_NAME = "kiosk_policy"
        private const val KEY_POLICY_JSON = "policy_json"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val DEFAULT_KIOSK_URL = "https://www.example.com"
    }
}
