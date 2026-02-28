package com.osamaalek.kiosklauncher.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
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
    private val backTapTimes = ArrayDeque<Long>()
    private var statusToast: Toast? = null
    private var backArmedUntilMs = 0L
    private var backConfirmUntilMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        // Opening app from launcher icon means user explicitly wants to enter kiosk again.
        if (!isLaunchedAsHome()) {
            KioskUtil.resumeKioskMode(this)
        }
        KioskUtil.startKioskMode(this, isLaunchedAsHome())

        findViewById<androidx.fragment.app.FragmentContainerView>(R.id.fragmentContainerView)
            .setOnLongClickListener {
                openSettingsWithPin()
                true
            }
    }
    override fun onStart() {
        super.onStart()
        if (!isLaunchedAsHome()) {
            KioskUtil.resumeKioskMode(this)
        }
        KioskUtil.startKioskMode(this, isLaunchedAsHome())
        val policy = PolicyStore(this).getPolicy()
        KioskScheduler.scheduleAll(this, policy)
        PolicySyncWorker.scheduleIfNeeded(this, policy)
    }
    override fun onResume() {
        super.onResume()
        val policy = PolicyStore(this).getPolicy()
        KioskUtil.applyWindowPolicy(this, policy.hideNavigationBar)
    }

    override fun onPause() {
        statusToast?.cancel()
        statusToast = null
        super.onPause()
    }

    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (fragment is AppsListFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerView, HomeFragment()).commit()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (fragment is AppsListFragment) {
            return super.onKeyDown(keyCode, event)
        }
        // Consume BACK in kiosk and handle on key up.
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) return super.onKeyUp(keyCode, event)
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (fragment is AppsListFragment) {
            return super.onKeyUp(keyCode, event)
        }
        val now = SystemClock.elapsedRealtime()

        if (isBackConfirmPending(now)) {
            backConfirmUntilMs = 0L
            backArmedUntilMs = 0L
            openSettingsWithPin()
            return true
        }

        if (backConfirmUntilMs != 0L && now > backConfirmUntilMs) {
            backConfirmUntilMs = 0L
            showStatusToast(getString(R.string.toast_back_confirm_timeout))
        }

        registerBackTap(now)
        return true
    }

    private fun registerBackTap(now: Long) {
        while (backTapTimes.isNotEmpty() && now - backTapTimes.first() > BACK_TAP_WINDOW_MS) {
            backTapTimes.removeFirst()
        }
        backTapTimes.addLast(now)

        val remain = BACK_TAP_COUNT - backTapTimes.size
        if (remain > 0) {
            showStatusToast(getString(R.string.toast_back_tap_remaining, remain))
            return
        }

        backTapTimes.clear()
        backArmedUntilMs = now + BACK_ARM_VALID_MS
        backConfirmUntilMs = now + BACK_CONFIRM_WINDOW_MS
        showStatusToast(getString(R.string.toast_back_admin_armed))
    }

    private fun isBackConfirmPending(now: Long): Boolean {
        return backArmedUntilMs != 0L &&
            now <= backArmedUntilMs &&
            backConfirmUntilMs != 0L &&
            now <= backConfirmUntilMs
    }

    private fun showStatusToast(message: String) {
        statusToast?.cancel()
        statusToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        statusToast?.show()
    }

    private fun isLaunchedAsHome(): Boolean {
        return intent?.categories?.contains(android.content.Intent.CATEGORY_HOME) == true
    }

    fun openSettingsWithPin() {
        val policy = PolicyStore(this).getPolicy()
        if (policy.exitPinHash.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.toast_set_pin_first), Toast.LENGTH_SHORT).show()
            startActivity(SettingsActivity.newIntent(this))
        } else {
            PinPrompt.verifyPin(this, policy.exitPinHash) {
                startActivity(SettingsActivity.newIntent(this))
            }
        }
    }

    companion object {
        private const val BACK_TAP_COUNT = 5
        private const val BACK_TAP_WINDOW_MS = 3000L
        private const val BACK_ARM_VALID_MS = 10000L
        private const val BACK_CONFIRM_WINDOW_MS = 5000L
    }
}