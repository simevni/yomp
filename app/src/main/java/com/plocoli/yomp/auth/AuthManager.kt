package com.plocoli.yomp.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class AuthState { UNKNOWN, UNAUTHENTICATED, AUTHENTICATING, AUTHENTICATED, ERROR }

/**
 * Singleton that holds the app-wide login state.
 * - launchLogin: opens the Spotify login page in a Custom Tab
 * - handleRedirect: exchanges code→token when yomp://callback arrives
 * - validAccessToken: returns a valid token, refreshing automatically if expired
 */
object AuthManager {

    private val _state = MutableStateFlow(AuthState.UNKNOWN)
    val state: StateFlow<AuthState> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var token: Token? = null
    private var pendingVerifier: String? = null
    private var pendingState: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val refreshMutex = Mutex()

    fun init(context: Context) {
        token = TokenStore.load(context)
        _state.value = if (token != null) AuthState.AUTHENTICATED else AuthState.UNAUTHENTICATED
    }

    val isConfigured: Boolean get() = SpotifyAuth.clientId.isNotBlank()

    fun launchLogin(context: Context) {
        if (!isConfigured) {
            _error.value = "Client ID 가 설정되지 않았어요 (local.properties)."
            _state.value = AuthState.ERROR
            return
        }
        val verifier = SpotifyAuth.newCodeVerifier()
        val state = UUID.randomUUID().toString()
        pendingVerifier = verifier
        pendingState = state
        _state.value = AuthState.AUTHENTICATING
        val url = SpotifyAuth.authorizeUrl(verifier, state)
        CustomTabsIntent.Builder().build().launchUrl(context, url)
    }

    /** Called from AuthCallbackActivity. */
    fun handleRedirect(context: Context, uri: Uri) {
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")
        val verifier = pendingVerifier

        if (error != null) {
            fail("로그인이 취소되었어요: $error")
            return
        }
        if (code == null || verifier == null || returnedState != pendingState) {
            fail("로그인 응답이 올바르지 않아요.")
            return
        }

        scope.launch {
            try {
                val t = SpotifyAuth.exchangeCode(code, verifier)
                token = t
                TokenStore.save(context, t)
                pendingVerifier = null
                pendingState = null
                _error.value = null
                _state.value = AuthState.AUTHENTICATED
            } catch (e: Exception) {
                fail("로그인에 실패했어요: ${e.message}")
            }
        }
    }

    /** A valid access token. Refreshes if expired. Null on failure. */
    suspend fun validAccessToken(context: Context): String? = refreshMutex.withLock {
        val current = token ?: return null
        if (!current.isExpired) return current.accessToken
        val rt = current.refreshToken ?: return null
        return try {
            val refreshed = SpotifyAuth.refresh(rt)
            token = refreshed
            TokenStore.save(context, refreshed)
            refreshed.accessToken
        } catch (e: Exception) {
            null
        }
    }

    fun logout(context: Context) {
        token = null
        TokenStore.clear(context)
        _state.value = AuthState.UNAUTHENTICATED
    }

    private fun fail(message: String) {
        _error.value = message
        _state.value = AuthState.ERROR
    }
}
