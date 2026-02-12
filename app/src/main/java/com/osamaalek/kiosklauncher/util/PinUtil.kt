package com.osamaalek.kiosklauncher.util

import java.security.MessageDigest

object PinUtil {
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashed.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, hash: String?): Boolean {
        if (hash.isNullOrBlank()) return false
        return hashPin(pin) == hash
    }
}
