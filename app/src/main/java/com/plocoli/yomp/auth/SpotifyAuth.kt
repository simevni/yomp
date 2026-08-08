package com.plocoli.yomp.auth

import android.net.Uri
import android.util.Base64
import com.plocoli.yomp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/** The token that gets stored/passed around. expiresAt is in epoch millis. */
@Serializable
data class Token(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt - 60_000 // 1-minute margin
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String = "Bearer",
)

/**
 * Pure logic for the Spotify Authorization Code + PKCE flow.
 * (AuthManager owns the state; AuthCallbackActivity receives the redirect.)
 */
object SpotifyAuth {

    private const val AUTHORIZE = "https://accounts.spotify.com/authorize"
    private const val TOKEN = "https://accounts.spotify.com/api/token"

    // Scopes needed for playback (App Remote) plus search/browsing.
    private val SCOPES = listOf(
        "app-remote-control",
        "streaming",
        "user-read-recently-played",
        "user-top-read",
        "user-library-read",
        "playlist-read-private",
    )
    // Note: user-library-modify (PUT /me/tracks) was deprecated in Spotify development mode as of 2026-02 (403) → likes are stored locally.

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()

    val clientId: String get() = BuildConfig.SPOTIFY_CLIENT_ID
    val redirectUri: String get() = BuildConfig.SPOTIFY_REDIRECT_URI

    // --- PKCE ---

    fun newCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun authorizeUrl(verifier: String, state: String): Uri =
        Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge(verifier))
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .appendQueryParameter("state", state)
            .build()

    // --- Token exchange/refresh (IO) ---

    suspend fun exchangeCode(code: String, verifier: String): Token = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()
        postToken(body)
    }

    suspend fun refresh(refreshToken: String): Token = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .build()
        postToken(body).let {
            // Spotify sometimes omits a new refresh_token, so keep the existing one.
            if (it.refreshToken == null) it.copy(refreshToken = refreshToken) else it
        }
    }

    private fun postToken(body: FormBody): Token {
        val req = Request.Builder().url(TOKEN).post(body).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("token request failed (${resp.code}): $text")
            val r = json.decodeFromString<TokenResponse>(text)
            return Token(
                accessToken = r.accessToken,
                refreshToken = r.refreshToken,
                expiresAt = System.currentTimeMillis() + r.expiresIn * 1000,
            )
        }
    }
}
