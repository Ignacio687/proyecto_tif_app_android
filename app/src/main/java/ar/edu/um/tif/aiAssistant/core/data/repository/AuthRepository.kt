package ar.edu.um.tif.aiAssistant.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ar.edu.um.tif.aiAssistant.core.data.model.*
import ar.edu.um.tif.aiAssistant.core.data.remote.KtorApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthRepository @Inject constructor(
    private val apiClient: KtorApiClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val EMAIL_KEY = stringPreferencesKey("email")
        private val NAME_KEY = stringPreferencesKey("name")
        private val IS_VERIFIED_KEY = stringPreferencesKey("is_verified")
    }

    // Get the stored JWT token
    val authToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    // Get the stored user ID
    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }

    // Get the stored email
    val email: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[EMAIL_KEY]
    }

    // Get the stored name
    val name: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[NAME_KEY]
    }

    // Get the stored verification status
    val isVerified: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[IS_VERIFIED_KEY]
    }

    // Method to save just the auth token (for compatibility with older code)
    suspend fun saveAuthData(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    // Authenticate with Google
    suspend fun authenticateWithGoogle(token: String): Result<AuthResponse> {
        val result = apiClient.authenticateWithGoogle(GoogleAuthRequest(token))

        result.onSuccess { authResponse ->
            saveAuthResponse(authResponse)
        }

        return result
    }

    // Register with email
    suspend fun registerWithEmail(email: String, username: String, password: String, name: String? = null): Result<Map<String, Any>> {
        return apiClient.registerWithEmail(
            EmailRegisterRequest(
                email = email,
                username = username,
                password = password,
                name = name
            )
        )
    }

    // Login with email
    suspend fun loginWithEmail(emailOrUsername: String, password: String): Result<AuthResponse> {
        val result = apiClient.loginWithEmail(
            EmailLoginRequest(
                email_or_username = emailOrUsername,
                password = password
            )
        )

        result.onSuccess { authResponse ->
            saveAuthResponse(authResponse)
        }

        return result
    }

    // Verify email
    suspend fun verifyEmail(code: String): Result<Map<String, Any>> {
        return apiClient.verifyEmail(EmailVerificationRequest(code))
    }

    // Resend verification code
    suspend fun resendVerificationCode(email: String): Result<Map<String, Any>> {
        return apiClient.resendVerificationCode(ResendVerificationRequest(email))
    }

    // Request password reset
    suspend fun requestPasswordReset(email: String): Result<Map<String, Any>> {
        return apiClient.requestPasswordReset(PasswordResetRequest(email))
    }

    // Confirm password reset
    suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Map<String, Any>> {
        return apiClient.confirmPasswordReset(
            PasswordResetConfirmRequest(
                code = code,
                new_password = newPassword
            )
        )
    }

    // Verify token
    suspend fun verifyToken(token: String): Result<Map<String, Any>> {
        return apiClient.verifyToken(token)
    }

    // Save auth response
    private suspend fun saveAuthResponse(authResponse: AuthResponse) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = authResponse.access_token
            preferences[USER_ID_KEY] = authResponse.user_id
            preferences[EMAIL_KEY] = authResponse.email
            authResponse.name?.let { preferences[NAME_KEY] = it }
            preferences[IS_VERIFIED_KEY] = authResponse.is_verified.toString()
        }
    }

    // Clear auth data (logout)
    suspend fun clearAuthData() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
            preferences.remove(EMAIL_KEY)
            preferences.remove(NAME_KEY)
            preferences.remove(IS_VERIFIED_KEY)
        }
    }
}
