package ar.edu.um.tif.aiAssistant.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels
import ar.edu.um.tif.aiAssistant.core.client.AuthApiClient
import ar.edu.um.tif.aiAssistant.core.customException.UnauthorizedAccessException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthRepository @Inject constructor(
    private val authClient: AuthApiClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val EMAIL_KEY = stringPreferencesKey("email")
        private val NAME_KEY = stringPreferencesKey("name")
        private val IS_VERIFIED_KEY = stringPreferencesKey("is_verified")
    }

    // Get the stored access token as a Flow
    val accessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY]
    }

    // Get the stored refresh token as a Flow
    val refreshToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_TOKEN_KEY]
    }

    // Get the current access token (suspending function)
    suspend fun getAccessToken(): String? {
        return accessToken.firstOrNull()
    }

    // Get the current refresh token (suspending function)
    suspend fun getRefreshToken(): String? {
        return refreshToken.firstOrNull()
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

    // Method to save tokens
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
        }
    }

    // Method to save only access token
    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = token
        }
    }

    // Authenticate with Google
    suspend fun authenticateWithGoogle(token: String): Result<ApiAuthModels.AuthResponse> {
        val result = authClient.authenticateWithGoogle(ApiAuthModels.GoogleAuthRequest(token))

        result.onSuccess { authResponse ->
            saveTokens(authResponse.accessToken, authResponse.refreshToken)
            saveUserData(authResponse)
        }

        return result
    }

    // Register with email
    suspend fun registerWithEmail(email: String, username: String, password: String, name: String? = null): Result<ApiAuthModels.RegisterResponse> {
        return authClient.registerWithEmail(
            ApiAuthModels.EmailRegisterRequest(
                email = email,
                username = username,
                password = password,
                name = name
            )
        )
    }

    // Login with email
    suspend fun loginWithEmail(emailOrUsername: String, password: String): Result<ApiAuthModels.AuthResponse> {
        val result = authClient.loginWithEmail(
            ApiAuthModels.EmailLoginRequest(
                emailOrUsername = emailOrUsername,
                password = password
            )
        )

        result.onSuccess { authResponse ->
            saveTokens(authResponse.accessToken, authResponse.refreshToken)
            saveUserData(authResponse)
        }

        return result
    }

    // Refresh token
    suspend fun refreshTokens(): Result<ApiAuthModels.AuthResponse> {
        val refreshToken = getRefreshToken() ?: return Result.failure(UnauthorizedAccessException("No refresh token available"))

        val result = authClient.refreshToken(ApiAuthModels.RefreshTokenRequest(refreshToken))

        result.onSuccess { authResponse ->
            saveTokens(authResponse.accessToken, authResponse.refreshToken)
        }

        return result
    }

    // Verify email
    suspend fun verifyEmail(code: String): Result<ApiAuthModels.VerificationResponse> {
        return authClient.verifyEmail(ApiAuthModels.EmailVerificationRequest(code))
    }

    // Resend verification code
    suspend fun resendVerificationCode(email: String): Result<ApiAuthModels.MessageResponse> {
        return authClient.resendVerificationCode(ApiAuthModels.ResendVerificationRequest(email))
    }

    // Request password reset
    suspend fun requestPasswordReset(email: String): Result<ApiAuthModels.MessageResponse> {
        return authClient.requestPasswordReset(ApiAuthModels.PasswordResetRequest(email))
    }

    // Confirm password reset
    suspend fun confirmPasswordReset(code: String, newPassword: String): Result<ApiAuthModels.MessageResponse> {
        return authClient.confirmPasswordReset(
            ApiAuthModels.PasswordResetConfirmRequest(
                code = code,
                newPassword = newPassword
            )
        )
    }

    // Verify token
    suspend fun verifyToken(token: String? = null): Result<ApiAuthModels.TokenVerificationResponse> {
        // Use provided token or get from storage
        val authToken = token ?: getAccessToken() ?: return Result.failure(IllegalStateException("No token available"))
        return authClient.verifyToken(authToken)
    }

    // Save user data from auth response (no longer saves the token)
    private suspend fun saveUserData(authResponse: ApiAuthModels.AuthResponse) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = authResponse.userId
            preferences[EMAIL_KEY] = authResponse.email
            authResponse.name?.let { preferences[NAME_KEY] = it }
            preferences[IS_VERIFIED_KEY] = authResponse.isVerified.toString()
        }
    }

    // Clear auth data (logout)
    suspend fun clearAuthData() {
        // Clear all data from DataStore
        context.dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
            preferences.remove(EMAIL_KEY)
            preferences.remove(NAME_KEY)
            preferences.remove(IS_VERIFIED_KEY)
        }
    }
}
