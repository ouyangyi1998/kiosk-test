package com.osamaalek.kiosklauncher.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.policy.PolicyStore
import com.osamaalek.kiosklauncher.scheduler.KioskScheduler
import com.osamaalek.kiosklauncher.settings.PinPrompt
import com.osamaalek.kiosklauncher.settings.SettingsActivity
import com.osamaalek.kiosklauncher.util.KioskUtil
import android.view.WindowManager
import android.widget.Toast
import com.osamaalek.kiosklauncher.workers.PolicySyncWorker

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        KioskUtil.startKioskMode(this)

        findViewById<androidx.fragment.app.FragmentContainerView>(R.id.fragmentContainerView)
            .setOnLongClickListener {
                openSettingsWithPin()
                true
            }
    }
    override fun onStart() {
        super.onStart()
        KioskUtil.startKioskMode(this)
        val policy = PolicyStore(this).getPolicy()
        KioskScheduler.scheduleAll(this, policy)
        PolicySyncWorker.scheduleIfNeeded(this, policy)
    }
    override fun onResume() {
        super.onResume()
        val policy = PolicyStore(this).getPolicy()
        KioskUtil.applyWindowPolicy(this, policy.hideNavigationBar)
    }
    override fun onBackPressed() {
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainerView) is AppsListFragment) supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerView, HomeFragment()).commit()
    }

    fun openSettingsWithPin() {
        val policy = PolicyStore(this).getPolicy()
        if (policy.exitPinHash.isNullOrBlank()) {
            Toast.makeText(this, "请先设置PIN", Toast.LENGTH_SHORT).show()
            startActivity(SettingsActivity.newIntent(this))
        } else {
            PinPrompt.verifyPin(this, policy.exitPinHash) {
                startActivity(SettingsActivity.newIntent(this))
            }
        }
    }
}