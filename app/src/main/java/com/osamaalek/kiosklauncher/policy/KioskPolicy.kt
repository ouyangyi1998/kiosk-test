package com.osamaalek.kiosklauncher.policy

data class KioskPolicy(
    val kioskUrl: String,
    val allowedPackages: Set<String>,
    val singleAppMode: Boolean,
    val disableStatusBar: Boolean,
    val disableNotifications: Boolean,
    val hideNavigationBar: Boolean,
    val exitPinHash: String?,
    val scheduleStart: String?,
    val scheduleStop: String?,
    val rebootTime: String?,
    val remoteUrl: String?,
    val remoteToken: String?
)
