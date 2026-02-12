package com.osamaalek.kiosklauncher.settings

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.osamaalek.kiosklauncher.util.PinUtil

object PinPrompt {
    fun verifyPin(context: Context, expectedHash: String?, onVerified: () -> Unit) {
        val input = EditText(context)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(context)
            .setTitle("请输入PIN")
            .setView(input)
            .setPositiveButton("确认") { _, _ ->
                val pin = input.text.toString()
                if (PinUtil.verifyPin(pin, expectedHash)) {
                    onVerified()
                } else {
                    Toast.makeText(context, "PIN错误", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
