package ar.edu.um.tif.aiAssistant.core.auth

import ar.edu.um.tif.aiAssistant.core.customException.UnauthorizedAccessException
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized authentication manager that handles token verification,
 * refresh operations, and auth state events across the application.
 */
@Singleton
class AuthManager @Inject constructor(
    private val authRepository: AuthRepository
) {
    // Shared flow to emit authentication events to all observers
    private val _authEvents = MutableSharedFlow<AuthEvent>(replay = 0)
    val authEvents: SharedFlow<AuthEvent> = _authEvents

    /**
     * Verify the current access token. If invalid, attempt to refresh it.
     * Returns true if authentication is valid after verification/refresh.
     * Emits AUTH_ERROR event if authentication fails completely.
     */
    suspend fun verifyAuthentication(): Boolean {
        try {
            // First check if we have an access token
            val accessToken = authRepository.getAccessToken()
            if (accessToken.isNullOrEmpty()) {
                // No access token, check if we have a refresh token
                val refreshToken = authRepository.getRefreshToken()
                if (refreshToken.isNullOrEmpty()) {
                    // No tokens at all, emit auth error
                    _authEvents.emit(AuthEvent.AUTH_ERROR)
                    return false
                } else {
                    // Try to refresh with the refresh token
                    return refreshTokens()
                }
            }

            // Verify the access token
            val verifyResult = authRepository.verifyToken()
            if (verifyResult.isSuccess && verifyResult.getOrNull()?.valid == true) {
                // Token is valid
                return true
            } else {
                // Token is invalid, try to refresh
                return refreshTokens()
            }
        } catch (e: Exception) {
            // Handle errors during verification
            handleAuthError(e)
            return false
        }
    }

    /**
     * Attempt to refresh tokens.
     * Returns true if refresh was successful, false otherwise.
     * Emits AUTH_ERROR event if refresh fails.
     */
    private suspend fun refreshTokens(): Boolean {
        return try {
            val refreshResult = authRepository.refreshTokens()
            if (refreshResult.isSuccess) {
                true
            } else {
                _authEvents.emit(AuthEvent.AUTH_ERROR)
                authRepository.clearAuthData()
                false
            }
        } catch (e: Exception) {
            handleAuthError(e)
            false
        }
    }

    /**
     * Handle authentication errors and emit appropriate events.
     */
    private suspend fun handleAuthError(e: Exception) {
        if (e is UnauthorizedAccessException) {
            authRepository.clearAuthData()
            _authEvents.emit(AuthEvent.AUTH_ERROR)
        }
    }

    /**
     * Perform logout operation.
     */
    suspend fun logout() {
        authRepository.clearAuthData()
        _authEvents.emit(AuthEvent.LOGGED_OUT)
    }

    /**
     * Authentication events that can be observed by components.
     */
    enum class AuthEvent {
        AUTH_ERROR,  // Authentication failed (e.g., expired tokens, invalid credentials)
        LOGGED_OUT,  // User explicitly logged out
    }
}
