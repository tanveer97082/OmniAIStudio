package com.omniai.studio.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.omniai.studio.security.VaultManager
import com.omniai.studio.terminal.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@Serializable
data class CloudUserProfile(
    val id: String = "",
    val email: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val role: String = "PENDING",
    @SerialName("is_approved") val isApproved: Boolean = false,
    @SerialName("last_device") val lastDevice: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

data class CloudSessionState(
    val isConfigured: Boolean = false,
    val isSignedIn: Boolean = false,
    val isOfflineLocalMode: Boolean = true,
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: CloudUserRole = CloudUserRole.OFFLINE_LOCAL,
    val isApproved: Boolean = false,
    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    val statusMessage: String = "Offline Local Mode"
)

/**
 * Pure OkHttp3 Supabase Google OAuth 2.0 PKCE + Server-Enforced RBAC Session Manager.
 * - Uses standard system browser (Intent.ACTION_VIEW) — ZERO Chrome Custom Tabs, ZERO Firebase/Play Services SDKs.
 * - Uses the build-bundled Supabase URL/anon key; stores JWTs, email, role, and PKCE verifier encrypted.
 */
class SupabaseAuthManager(
    private val vault: VaultManager,
    private val roleGuard: RolePermissionGuard
) {

    companion object {
        const val REDIRECT_SCHEME = "omniaistudio"
        const val REDIRECT_HOST = "auth-callback"
        const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"
        private const val PKCE_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        private const val PKCE_VERIFIER_LENGTH = 64
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val _sessionState = MutableStateFlow(buildSessionFromVault())
    val sessionState: StateFlow<CloudSessionState> = _sessionState.asStateFlow()

    init {
        if (isConfigured() && vault.cloudAccessToken.isNotBlank() && !vault.cloudOfflineLocalMode) {
            vault.cloudUserRole = "PENDING"
            vault.cloudIsApproved = false
            refreshLocalSessionSnapshot()
        }
        roleGuard.updateRole(_sessionState.value.role)
    }

    fun isConfigured(): Boolean {
        val url = normalizedBaseUrl()
        return url.startsWith("https://") && vault.supabaseAnonKey.isNotBlank()
    }

    fun normalizedBaseUrl(): String = vault.supabaseUrl.trim().trimEnd('/')

    fun deviceDisplayName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.ifBlank { "Android Device" }
        } else {
            "$manufacturer $model".trim().ifBlank { "Android Device" }
        }
    }

    fun refreshLocalSessionSnapshot(): CloudSessionState {
        val snapshot = buildSessionFromVault()
        _sessionState.value = snapshot
        roleGuard.updateRole(snapshot.role)
        return snapshot
    }

    private fun buildSessionFromVault(): CloudSessionState {
        val configured = isConfigured()
        val hasToken = vault.cloudAccessToken.isNotBlank() && vault.cloudRefreshToken.isNotBlank()
        val offline = vault.cloudOfflineLocalMode || !hasToken || !configured
        val resolvedRole = if (offline) {
            CloudUserRole.OFFLINE_LOCAL
        } else {
            CloudUserRole.fromServerRole(
                role = vault.cloudUserRole,
                isApproved = vault.cloudIsApproved,
                isOfflineLocal = false
            )
        }
        val status = when {
            !configured && offline -> "Offline Local Mode (Supabase is not configured in this build)"
            offline -> "Offline Local Mode (Sign in with Google to sync)"
            resolvedRole == CloudUserRole.PENDING -> "Signed in as ${vault.cloudUserEmail} — Awaiting Owner Approval"
            else -> "Signed in as ${vault.cloudUserEmail} (${resolvedRole.badgeText})"
        }
        return CloudSessionState(
            isConfigured = configured,
            isSignedIn = hasToken && !offline,
            isOfflineLocalMode = offline,
            userId = vault.cloudUserId,
            email = vault.cloudUserEmail,
            displayName = vault.cloudUserDisplayName,
            role = resolvedRole,
            isApproved = vault.cloudIsApproved,
            deviceName = deviceDisplayName(),
            statusMessage = status
        )
    }

    /**
     * Generates a cryptographically strong 64-character URL-safe PKCE code_verifier.
     */
    fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val sb = StringBuilder(PKCE_VERIFIER_LENGTH)
        repeat(PKCE_VERIFIER_LENGTH) {
            sb.append(PKCE_CHARSET[random.nextInt(PKCE_CHARSET.length)])
        }
        return sb.toString()
    }

    /**
     * Computes RFC 7636 S256 PKCE code_challenge: Base64UrlNoPadding(SHA-256(code_verifier)).
     */
    fun computeCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    /**
     * Starts Google OAuth 2.0 PKCE flow using the user's standard system browser (Intent.ACTION_VIEW).
     * Strictly NO Chrome Custom Tabs and NO embedded WebView.
     */
    fun startGoogleSignIn(context: Context): Result<String> {
        val baseUrl = normalizedBaseUrl()
        if (!baseUrl.startsWith("https://")) {
            return Result.failure(IllegalStateException("Supabase is not configured in this build."))
        }
        if (vault.supabaseAnonKey.isBlank()) {
            return Result.failure(IllegalStateException("Supabase is not configured in this build."))
        }

        val verifier = generateCodeVerifier()
        val challenge = computeCodeChallenge(verifier)
        vault.cloudPkceVerifier = verifier

        val encodedRedirect = URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8.name())
        val encodedChallenge = URLEncoder.encode(challenge, StandardCharsets.UTF_8.name())
        val authUrl = "$baseUrl/auth/v1/authorize" +
            "?provider=google" +
            "&redirect_to=$encodedRedirect" +
            "&code_challenge=$encodedChallenge" +
            "&code_challenge_method=s256"

        return runCatching {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            "Opened system browser for Google Sign-In (PKCE S256)..."
        }
    }

    fun isAuthCallbackUri(uri: Uri?): Boolean {
        if (uri == null) return false
        return uri.scheme.equals(REDIRECT_SCHEME, ignoreCase = true) &&
            uri.host.equals(REDIRECT_HOST, ignoreCase = true)
    }

    /**
     * Exchanges the authorization `code` from `omniaistudio://auth-callback` for Supabase JWTs,
     * fetches server-enforced RBAC role from `user_profiles`, and updates device telemetry.
     */
    suspend fun handleAuthCallback(uri: Uri): Result<CloudSessionState> = withContext(Dispatchers.IO) {
        if (!isAuthCallbackUri(uri)) {
            return@withContext Result.failure(IllegalArgumentException("Unrecognized auth callback URI"))
        }

        val oauthError = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
        if (!oauthError.isNullOrBlank()) {
            vault.cloudPkceVerifier = ""
            return@withContext Result.failure(IllegalStateException("Google Sign-In failed: $oauthError"))
        }

        val code = uri.getQueryParameter("code")?.trim().orEmpty()
        if (code.isBlank()) {
            return@withContext Result.failure(IllegalStateException("OAuth callback missing authorization code."))
        }

        val verifier = vault.cloudPkceVerifier.trim()
        if (verifier.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing PKCE code_verifier. Tap 'Sign in with Google' again."))
        }

        val baseUrl = normalizedBaseUrl()
        val anonKey = vault.supabaseAnonKey.trim()
        if (!baseUrl.startsWith("https://") || anonKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Supabase URL or Anon Key is not configured."))
        }

        try {
            val payload = buildJsonObject {
                put("auth_code", code)
                put("code_verifier", verifier)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=pkce")
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val tokenObj = client.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Supabase PKCE token exchange failed (HTTP ${resp.code}): ${rawBody.take(220)}")
                }
                json.parseToJsonElement(rawBody).jsonObject
            }

            // Clear single-use PKCE verifier immediately after exchange
            vault.cloudPkceVerifier = ""

            persistTokenResponse(tokenObj)
            vault.cloudOfflineLocalMode = false
            vault.cloudUserRole = "PENDING"
            vault.cloudIsApproved = false

            ensureOwnProfileInternal(vault.cloudAccessToken, vault.cloudUserId)
            val profile = fetchOwnProfileInternal(vault.cloudAccessToken, vault.cloudUserId)
                ?: throw IllegalStateException("Supabase did not return this account's user_profiles row.")
            applyProfileToVault(profile)
            if (profile.isApproved) {
                updateOwnDeviceHeartbeatInternal(vault.cloudAccessToken, vault.cloudUserId)
            }

            val updated = refreshLocalSessionSnapshot()
            Result.success(updated)
        } catch (ex: Exception) {
            vault.cloudPkceVerifier = ""
            refreshLocalSessionSnapshot()
            Result.failure(ex)
        }
    }

    /**
     * Returns a fresh access_token, automatically refreshing via refresh_token when near expiry.
     */
    suspend fun ensureValidAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase URL & Anon Key not configured."))
        }
        val currentAccess = vault.cloudAccessToken
        val currentRefresh = vault.cloudRefreshToken
        if (currentAccess.isBlank() || currentRefresh.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Not signed in to Supabase Cloud DB."))
        }

        val now = System.currentTimeMillis()
        if (vault.cloudTokenExpiresAtMs > now + 60_000L) {
            return@withContext Result.success(currentAccess)
        }

        val baseUrl = normalizedBaseUrl()
        val anonKey = vault.supabaseAnonKey.trim()
        try {
            val payload = buildJsonObject {
                put("refresh_token", currentRefresh)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val tokenObj = client.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Session refresh failed (HTTP ${resp.code}): ${rawBody.take(180)}")
                }
                json.parseToJsonElement(rawBody).jsonObject
            }

            persistTokenResponse(tokenObj)
            Result.success(vault.cloudAccessToken)
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    /**
     * Re-fetches the signed-in user's role & approval status from Supabase `user_profiles`.
     */
    suspend fun refreshProfileStatus(): Result<CloudSessionState> = withContext(Dispatchers.IO) {
        val token = ensureValidAccessToken().getOrElse {
            markProfileUnverified()
            return@withContext Result.failure(it)
        }
        val userId = vault.cloudUserId
        if (userId.isBlank()) {
            markProfileUnverified()
            return@withContext Result.failure(IllegalStateException("Missing cloud user ID; please sign in again."))
        }
        try {
            ensureOwnProfileInternal(token, userId)
            val profile = fetchOwnProfileInternal(token, userId)
                ?: run {
                    markProfileUnverified()
                    return@withContext Result.failure(IllegalStateException("Supabase did not return this account's user_profiles row."))
                }
            applyProfileToVault(profile)
            if (profile.isApproved) updateOwnDeviceHeartbeatInternal(token, userId)
            val updated = refreshLocalSessionSnapshot()
            Result.success(updated)
        } catch (ex: Exception) {
            markProfileUnverified()
            Result.failure(ex)
        }
    }

    /**
     * OWNER-only: fetches all registered accounts and devices from `public.user_profiles`.
     * Server-side RLS rejects non-OWNER callers even if called directly.
     */
    suspend fun listAllUserProfiles(): Result<List<CloudUserProfile>> = withContext(Dispatchers.IO) {
        val verifiedSession = refreshProfileStatus().getOrElse { return@withContext Result.failure(it) }
        val deny = roleGuard.denyReasonForAdmin()
        if (!verifiedSession.isApproved || deny != null) {
            return@withContext Result.failure(IllegalStateException(deny ?: "Approved OWNER role required."))
        }
        val token = ensureValidAccessToken().getOrElse { return@withContext Result.failure(it) }
        try {
            val req = Request.Builder()
                .url("${normalizedBaseUrl()}/rest/v1/user_profiles?select=*&order=created_at.asc")
                .header("apikey", vault.supabaseAnonKey.trim())
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Failed to fetch team profiles (HTTP ${resp.code}): ${body.take(180)}")
                }
                val list = json.decodeFromString<List<CloudUserProfile>>(body)
                Result.success(list)
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    /**
     * OWNER-only: updates a user's RBAC role & approval flag via PostgREST PATCH.
     */
    suspend fun updateUserRoleAndApproval(
        targetUserId: String,
        newRole: String,
        isApproved: Boolean
    ): Result<CloudUserProfile> = withContext(Dispatchers.IO) {
        val normalizedRole = newRole.uppercase(Locale.US).trim()
        if (normalizedRole !in setOf("EDITOR", "VIEWER", "PENDING")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid role '$newRole'"))
        }

        val verifiedSession = refreshProfileStatus().getOrElse { return@withContext Result.failure(it) }
        val deny = roleGuard.denyReasonForAdmin()
        if (!verifiedSession.isApproved || deny != null) {
            return@withContext Result.failure(IllegalStateException(deny ?: "Approved OWNER role required."))
        }
        val token = ensureValidAccessToken().getOrElse { return@withContext Result.failure(it) }
        try {
            val encodedId = URLEncoder.encode(targetUserId, StandardCharsets.UTF_8.name())
            val payload = buildJsonObject {
                put("role", normalizedRole)
                put("is_approved", if (normalizedRole == "PENDING") false else isApproved)
            }.toString()

            val req = Request.Builder()
                .url("${normalizedBaseUrl()}/rest/v1/user_profiles?id=eq.$encodedId")
                .header("apikey", vault.supabaseAnonKey.trim())
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .patch(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Role update rejected (HTTP ${resp.code}): ${body.take(180)}")
                }
                val rows = json.decodeFromString<List<CloudUserProfile>>(body)
                val updated = rows.firstOrNull()
                    ?: throw IllegalStateException("No user_profiles row updated (RLS denied or unknown user)")
                if (updated.id == vault.cloudUserId) {
                    applyProfileToVault(updated)
                    refreshLocalSessionSnapshot()
                }
                Result.success(updated)
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    fun enableOfflineLocalMode(): CloudSessionState {
        vault.cloudOfflineLocalMode = true
        return refreshLocalSessionSnapshot()
    }

    /**
     * Calls Supabase `/auth/v1/logout` (best-effort) and wipes local cloud tokens from EncryptedSharedPreferences.
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = normalizedBaseUrl()
        val anonKey = vault.supabaseAnonKey.trim()
        val accessToken = vault.cloudAccessToken

        if (baseUrl.startsWith("https://") && anonKey.isNotBlank() && accessToken.isNotBlank()) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/logout")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $accessToken")
                    .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(req).execute().close()
            }
        }

        vault.clearCloudSession()
        refreshLocalSessionSnapshot()
        Result.success(Unit)
    }

    private fun persistTokenResponse(tokenObj: JsonObject) {
        val accessToken = tokenObj["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val refreshToken = tokenObj["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val expiresInSec = tokenObj["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600
        if (accessToken.isBlank()) {
            throw IllegalStateException("Supabase response did not include access_token")
        }

        vault.cloudAccessToken = accessToken
        if (refreshToken.isNotBlank()) {
            vault.cloudRefreshToken = refreshToken
        }
        vault.cloudTokenExpiresAtMs = System.currentTimeMillis() + (expiresInSec * 1000L)

        val userObj = tokenObj["user"]?.jsonObject
        if (userObj != null) {
            val uid = userObj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val email = userObj["email"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val meta = userObj["user_metadata"]?.jsonObject
            val displayName = meta?.get("full_name")?.jsonPrimitive?.contentOrNull
                ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
                ?: email.substringBefore("@")
            if (uid.isNotBlank()) vault.cloudUserId = uid
            if (email.isNotBlank()) vault.cloudUserEmail = email
            if (displayName.isNotBlank()) vault.cloudUserDisplayName = displayName
        }
    }

    private fun fetchOwnProfileInternal(accessToken: String, userId: String): CloudUserProfile? {
        val baseUrl = normalizedBaseUrl()
        val anonKey = vault.supabaseAnonKey.trim()
        val encodedId = URLEncoder.encode(userId, StandardCharsets.UTF_8.name())

        val req = Request.Builder()
            .url("$baseUrl/rest/v1/user_profiles?id=eq.$encodedId&select=*")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Failed to fetch user_profiles (HTTP ${resp.code}): ${body.take(180)}")
            }
            val list = json.decodeFromString<List<CloudUserProfile>>(body)
            return list.firstOrNull()
        }
    }

    private fun ensureOwnProfileInternal(accessToken: String, userId: String) {
        if (userId.isBlank()) {
            throw IllegalStateException("Missing cloud user ID; please sign in again.")
        }
        val request = Request.Builder()
            .url("${normalizedBaseUrl()}/rest/v1/rpc/ensure_user_profile")
            .header("apikey", vault.supabaseAnonKey.trim())
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Could not verify or create user_profiles row (HTTP ${response.code}): ${body.take(180)}")
            }
        }
    }

    private fun updateOwnDeviceHeartbeatInternal(accessToken: String, userId: String) {
        val baseUrl = normalizedBaseUrl()
        val anonKey = vault.supabaseAnonKey.trim()
        val encodedId = URLEncoder.encode(userId, StandardCharsets.UTF_8.name())
        val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val payload = buildJsonObject {
            put("last_device", deviceDisplayName())
            put("last_seen_at", isoNow)
        }.toString()

        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/user_profiles?id=eq.$encodedId")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .patch(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().close()
        }
    }

    private fun applyProfileToVault(profile: CloudUserProfile) {
        vault.cloudUserRole = profile.role.uppercase(Locale.US)
        vault.cloudIsApproved = profile.isApproved
        if (profile.email.isNotBlank()) vault.cloudUserEmail = profile.email
        if (!profile.displayName.isNullOrBlank()) vault.cloudUserDisplayName = profile.displayName
    }

    private fun markProfileUnverified() {
        vault.cloudUserRole = "PENDING"
        vault.cloudIsApproved = false
        refreshLocalSessionSnapshot()
    }
}
