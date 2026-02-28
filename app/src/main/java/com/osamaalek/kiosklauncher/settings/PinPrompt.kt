package com.osamaalek.kiosklauncher.settings

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.util.PinUtil

object PinPrompt {
    fun verifyPin(context: Context, expectedHash: String?, onVerified: () -> Unit) {
        val remaining = remainingLockSeconds(context)
        if (remaining > 0) {
            Toast.makeText(
                context,
                context.getString(R.string.error_pin_locked_seconds, remaining),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val input = EditText(context)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.title_enter_pin)
            .setView(input)
            .setPositiveButton(R.string.button_confirm, null)
            .setNegativeButton(R.string.button_cancel, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val seconds = remainingLockSeconds(context)
            if (seconds > 0) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_pin_locked_seconds, seconds),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val pin = input.text.toString()
            if (PinUtil.verifyPin(pin, expectedHash)) {
                resetPinGuard(context)
                dialog.dismiss()
                onVerified()
                return@setOnClickListener
            }

            val left = onPinFailed(context)
            if (left <= 0) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_pin_locked_seconds, LOCKOUT_DURATION_SECONDS),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            } else {
                input.setText("")
                Toast.makeText(
                    context,
                    context.getString(R.string.error_pin_wrong_with_remaining, left),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PIN_GUARD_PREFS, Context.MODE_PRIVATE)

    private fun remainingLockSeconds(context: Context): Long {
        val lockUntil = prefs(context).getLong(KEY_LOCK_UNTIL, 0L)
        val now = System.currentTimeMillis()
        if (lockUntil <= now) return 0L
        return ((lockUntil - now) + 999L) / 1000L
    }

    private fun onPinFailed(context: Context): Int {
        val pref = prefs(context)
        val failCount = pref.getInt(KEY_FAIL_COUNT, 0) + 1
        return if (failCount >= MAX_FAIL_ATTEMPTS) {
            pref.edit()
                .putInt(KEY_FAIL_COUNT, 0)
                .putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + LOCKOUT_DURATION_MS)
                .apply()
            0
        } else {
            pref.edit().putInt(KEY_FAIL_COUNT, failCount).apply()
            MAX_FAIL_ATTEMPTS - failCount
        }
    }

    private fun resetPinGuard(context: Context) {
        prefs(context).edit()
            .putInt(KEY_FAIL_COUNT, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    private const val PIN_GUARD_PREFS = "pin_guard"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_LOCK_UNTIL = "lock_until"
    private const val MAX_FAIL_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_SECONDS = 60
    private const val LOCKOUT_DURATION_MS = LOCKOUT_DURATION_SECONDS * 1000L
}
