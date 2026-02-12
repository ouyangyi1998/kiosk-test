package com.osamaalek.kiosklauncher.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.osamaalek.kiosklauncher.receivers.KioskScheduleReceiver
import com.osamaalek.kiosklauncher.util.KioskUtil

class KioskControlActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            KioskScheduleReceiver.ACTION_START -> KioskUtil.startKioskMode(this)
            KioskScheduleReceiver.ACTION_STOP -> KioskUtil.stopKioskMode(this)
        }
        finish()
    }
}
