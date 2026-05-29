package com.khetika.khetikasync.data.auth

import java.security.MessageDigest

object PinHasher {
    /**
     * Stable hash for storing a 4-digit PIN.
     * `email` is used as a per-user salt so two users with the same PIN
     * still get different hashes.
     *
     * Note: 4 digits = 10k possibilities. If pin_hash leaks, brute-forcing
     * recovers the PIN trivially. Treat as convenience, not security.
     */
    fun hash(pin: String, email: String): String {
        val input = pin.trim() + ":" + email.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
