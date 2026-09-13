package com.parallel.app.hub.security

import java.security.SecureRandom
import java.util.Base64

class PairingManager {
    private val random = SecureRandom()
    @Volatile private var token: String = generateToken()

    fun rotate() { token = generateToken() }
    fun currentToken(): String = token
    fun verify(candidate: String): Boolean = candidate.isNotBlank() && candidate == token
    private fun generateToken(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also(random::nextBytes))
}
