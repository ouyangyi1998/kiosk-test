package com.osamaalek.kiosklauncher.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.osamaalek.kiosklauncher.receivers.KioskScheduleReceiver
import com.osamaalek.kiosklauncher.util.KioskUtil

class KioskControlActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            KioskScheduleReceiver.ACTION_START -> {
                KioskUtil.resumeKioskMode(this)
                val mainIntent = Intent(this, MainActivity::class.java)
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(mainIntent)
            }
            KioskScheduleReceiver.ACTION_STOP -> KioskUtil.stopKioskMode(this)
        }
        finish()
    }
}
