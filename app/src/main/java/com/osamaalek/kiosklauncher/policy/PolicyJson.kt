package com.osamaalek.kiosklauncher.policy

import org.json.JSONArray
import org.json.JSONObject

data class PolicyUpdate(
    val kioskUrl: String?,
    val allowedPackages: Set<String>?,
    val disableStatusBar: Boolean?,
    val disableNotifications: Boolean?,
    val hideNavigationBar: Boolean?,
    val exitPinHash: String?,
    val scheduleStart: String?,
    val scheduleStop: String?,
    val rebootTime: String?,
    val remoteUrl: String?,
    val remoteToken: String?
) {
    fun applyTo(existing: KioskPolicy): KioskPolicy {
        return existing.copy(
            kioskUrl = kioskUrl ?: existing.kioskUrl,
            allowedPackages = allowedPackages ?: existing.allowedPackages,
            disableStatusBar = disableStatusBar ?: existing.disableStatusBar,
            disableNotifications = disableNotifications ?: existing.disableNotifications,
            hideNavigationBar = hideNavigationBar ?: existing.hideNavigationBar,
            exitPinHash = exitPinHash ?: existing.exitPinHash,
            scheduleStart = scheduleStart ?: existing.scheduleStart,
            scheduleStop = scheduleStop ?: existing.scheduleStop,
            rebootTime = rebootTime ?: existing.rebootTime,
            remoteUrl = remoteUrl ?: existing.remoteUrl,
            remoteToken = remoteToken ?: existing.remoteToken
        )
    }
}

object PolicyJson {
    fun toJson(policy: KioskPolicy): String {
        val json = JSONObject()
        json.put("kioskUrl", policy.kioskUrl)
        json.put("allowedPackages", JSONArray(policy.allowedPackages.toList()))
        json.put("disableStatusBar", policy.disableStatusBar)
        json.put("disableNotifications", policy.disableNotifications)
        json.put("hideNavigationBar", policy.hideNavigationBar)
        json.put("exitPinHash", policy.exitPinHash)
        json.put("scheduleStart", policy.scheduleStart)
        json.put("scheduleStop", policy.scheduleStop)
        json.put("rebootTime", policy.rebootTime)
        json.put("remoteUrl", policy.remoteUrl)
        json.put("remoteToken", policy.remoteToken)
        return json.toString()
    }

    fun parseUpdate(jsonString: String): PolicyUpdate {
        val json = JSONObject(jsonString)
        return PolicyUpdate(
            kioskUrl = stringOrNull(json, "kioskUrl"),
            allowedPackages = stringSetOrNull(json, "allowedPackages"),
            disableStatusBar = booleanOrNull(json, "disableStatusBar"),
            disableNotifications = booleanOrNull(json, "disableNotifications"),
            hideNavigationBar = booleanOrNull(json, "hideNavigationBar"),
            exitPinHash = stringOrNull(json, "exitPinHash"),
            scheduleStart = stringOrNull(json, "scheduleStart"),
            scheduleStop = stringOrNull(json, "scheduleStop"),
            rebootTime = stringOrNull(json, "rebootTime"),
            remoteUrl = stringOrNull(json, "remoteUrl"),
            remoteToken = stringOrNull(json, "remoteToken")
        )
    }

    private fun stringOrNull(json: JSONObject, key: String): String? {
        if (!json.has(key)) return null
        return if (json.isNull(key)) null else json.optString(key, null)
    }

    private fun booleanOrNull(json: JSONObject, key: String): Boolean? {
        if (!json.has(key)) return null
        return if (json.isNull(key)) null else json.optBoolean(key)
    }

    private fun stringSetOrNull(json: JSONObject, key: String): Set<String>? {
        if (!json.has(key)) return null
        if (json.isNull(key)) return null
        val array = json.optJSONArray(key) ?: return null
        val result = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, null)
            if (!value.isNullOrBlank()) {
                result.add(value)
            }
        }
        return result
    }
}
