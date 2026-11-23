package com.omniai.studio.data.remote

import com.omniai.studio.security.VaultManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class ApiPoolProvider(val label: String) {
    GEMINI("Gemini"),
    HUGGING_FACE("Hugging Face"),
    PRIMARY_TUNNEL("Primary Tunnel")
}

data class ApiKeyPoolStatus(
    val provider: ApiPoolProvider,
    val totalKeys: Int,
    val healthyKeys: Int,
    val coolingDownKeys: Int
)

/**
 * v5.0 Multi-Key API Pool Manager:
 * Parses comma- or newline-separated API keys stored in the encrypted Vault, performs thread-safe
 * round-robin selection across healthy keys, and applies automatic cooldowns on HTTP 429 / 401 / 403
 * responses so multi-key creators never stall on single-key rate limits.
 */
class ApiKeyPoolManager(
    private val vault: VaultManager
) {
    private val roundRobinCounters = ConcurrentHashMap<ApiPoolProvider, AtomicInteger>()
    private val cooldownUntilMsByKeyHash = ConcurrentHashMap<String, Long>()

    fun parseKeyList(rawValue: String): List<String> =
        rawValue
            .split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    fun getConfiguredKeys(provider: ApiPoolProvider): List<String> = when (provider) {
        ApiPoolProvider.GEMINI -> parseKeyList(vault.geminiApiKey)
        ApiPoolProvider.HUGGING_FACE -> parseKeyList(vault.huggingFaceToken)
        ApiPoolProvider.PRIMARY_TUNNEL -> parseKeyList(vault.primaryTunnelApiKey)
    }

    /**
     * Selects the next healthy key for [provider] using round-robin rotation.
     * If every key is currently cooling down, returns the key whose cooldown expires soonest.
     */
    fun nextHealthyKey(provider: ApiPoolProvider): String? {
        val keys = getConfiguredKeys(provider)
        if (keys.isEmpty()) return null
        if (keys.size == 1) return keys.first()

        val now = System.currentTimeMillis()
        val healthy = keys.filter { key ->
            val until = cooldownUntilMsByKeyHash[keyFingerprint(provider, key)] ?: 0L
            until <= now
        }

        if (healthy.isNotEmpty()) {
            val counter = roundRobinCounters.getOrPut(provider) { AtomicInteger(0) }
            val idx = Math.floorMod(counter.getAndIncrement(), healthy.size)
            return healthy[idx]
        }

        return keys.minByOrNull { key ->
            cooldownUntilMsByKeyHash[keyFingerprint(provider, key)] ?: 0L
        }
    }

    fun reportRateLimitOrAuthFailure(
        provider: ApiPoolProvider,
        failedKey: String,
        httpCode: Int,
        retryAfterSeconds: Long? = null
    ) {
        val clean = failedKey.trim()
        if (clean.isEmpty()) return
        val cooldownMs = when {
            retryAfterSeconds != null && retryAfterSeconds > 0L ->
                (retryAfterSeconds * 1_000L).coerceIn(15_000L, 15 * 60_000L)
            httpCode == 429 -> DEFAULT_RATE_LIMIT_COOLDOWN_MS
            httpCode == 401 || httpCode == 403 -> DEFAULT_AUTH_ERROR_COOLDOWN_MS
            else -> DEFAULT_TRANSIENT_COOLDOWN_MS
        }
        cooldownUntilMsByKeyHash[keyFingerprint(provider, clean)] =
            System.currentTimeMillis() + cooldownMs
    }

    fun reportSuccess(provider: ApiPoolProvider, key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) return
        cooldownUntilMsByKeyHash.remove(keyFingerprint(provider, clean))
    }

    fun clearCooldowns(provider: ApiPoolProvider? = null) {
        if (provider == null) {
            cooldownUntilMsByKeyHash.clear()
        } else {
            val prefix = "${provider.name}:"
            cooldownUntilMsByKeyHash.keys.removeAll { it.startsWith(prefix) }
        }
    }

    fun inspectPoolStatus(): List<ApiKeyPoolStatus> {
        val now = System.currentTimeMillis()
        return ApiPoolProvider.entries.map { provider ->
            val keys = getConfiguredKeys(provider)
            val cooling = keys.count { key ->
                (cooldownUntilMsByKeyHash[keyFingerprint(provider, key)] ?: 0L) > now
            }
            ApiKeyPoolStatus(
                provider = provider,
                totalKeys = keys.size,
                healthyKeys = (keys.size - cooling).coerceAtLeast(0),
                coolingDownKeys = cooling
            )
        }
    }

    private fun keyFingerprint(provider: ApiPoolProvider, key: String): String =
        "${provider.name}:${key.hashCode()}:${key.length}"

    companion object {
        private const val DEFAULT_RATE_LIMIT_COOLDOWN_MS = 65_000L
        private const val DEFAULT_AUTH_ERROR_COOLDOWN_MS = 10 * 60_000L
        private const val DEFAULT_TRANSIENT_COOLDOWN_MS = 20_000L
    }
}
