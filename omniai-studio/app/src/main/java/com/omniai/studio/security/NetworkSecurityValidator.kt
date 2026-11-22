package com.omniai.studio.security

import java.net.URI

/**
 * Enforces HTTPS for all remote endpoints while explicitly permitting HTTP only
 * for local loopback Termux/LAN development servers (`localhost` or `127.0.0.1`).
 */
object NetworkSecurityValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val normalizedUrl: String = "",
        val errorMessage: String? = null
    )

    private val LOCAL_LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")

    fun validateEndpointUrl(rawUrl: String): ValidationResult {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return ValidationResult(false, errorMessage = "Endpoint URL cannot be empty.")
        }

        val uri = try {
            URI(trimmed)
        } catch (ex: Exception) {
            return ValidationResult(false, errorMessage = "Malformed URL: ${ex.message}")
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        if (host.isNullOrBlank()) {
            return ValidationResult(false, errorMessage = "URL must contain a valid hostname.")
        }

        return when (scheme) {
            "https" -> ValidationResult(true, normalizedUrl = trimmed)
            "http" -> {
                if (host in LOCAL_LOOPBACK_HOSTS) {
                    ValidationResult(true, normalizedUrl = trimmed)
                } else {
                    ValidationResult(
                        isValid = false,
                        errorMessage = "Insecure HTTP blocked for remote host '$host'. Use HTTPS (HTTP allowed only for localhost / 127.0.0.1)."
                    )
                }
            }
            else -> ValidationResult(
                isValid = false,
                errorMessage = "Unsupported scheme '$scheme'. Only HTTPS (or HTTP for localhost) is permitted."
            )
        }
    }
}
