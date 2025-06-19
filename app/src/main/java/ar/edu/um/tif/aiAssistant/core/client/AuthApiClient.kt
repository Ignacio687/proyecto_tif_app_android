package ar.edu.um.tif.aiAssistant.core.client

import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels.AuthResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels.EmailLoginRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels.EmailRegisterRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels.EmailVerificationRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels.GoogleAuthRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels.PasswordResetConfirmRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels.PasswordResetRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels.ResendVerificationRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAuthModels.VerificationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for handling all authentication-related API calls.
 */
@Singleton
class AuthApiClient @Inject constructor(
    private val client: HttpClient,
    private val json: Json
) {
    // API base paths
    private val apiPath = "/api/v1/auth"

    /**
     * Authenticate with a Google account using the provided token
     */
    suspend fun authenticateWithGoogle(request: GoogleAuthRequest): Result<AuthResponse> = runCatching {
        val response = client.post {
            url("$apiPath/google")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.body()
    }

    /**
     * Verify the validity of an authentication token
     */
    suspend fun verifyToken(token: String): Result<Map<String, Any>> = runCatching {
        val response = client.post {
            url("$apiPath/verify-token")
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        response.body()
    }

    /**
     * Register a new user with email, username, and password
     */
    suspend fun registerWithEmail(request: EmailRegisterRequest): Result<AuthResponse> = runCatching {
        val response = client.post {
            url("$apiPath/register")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.body()
    }

    /**
     * Log in with email/username and password
     */
    suspend fun loginWithEmail(request: EmailLoginRequest): Result<AuthResponse> = runCatching {
        val response = client.post {
            url("$apiPath/login")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val responseText = response.bodyAsText()
        try {
            json.decodeFromString<AuthResponse>(responseText)
        } catch (e: Exception) {
            // Log the detailed error for debugging
            android.util.Log.e("AuthApiClient", "Login parsing error: ${e.message}", e)
            android.util.Log.d("AuthApiClient", "Response body: $responseText")

            // Throw a user-friendly error message
            throw Exception("Please try again later.")
        }
    }

    /**
     * Verify a user's email with a verification code
     */
    suspend fun verifyEmail(request: EmailVerificationRequest): Result<VerificationResponse> = runCatching {
        val response = client.post {
            url("$apiPath/verify-email")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.body()
    }

    /**
     * Request a new verification code to be sent to the user's email
     */
    suspend fun resendVerificationCode(request: ResendVerificationRequest): Result<VerificationResponse> = runCatching {
        val response = client.post {
            url("$apiPath/resend-verification")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.body()
    }

    /**
     * Request a password reset via email
     */
    suspend fun requestPasswordReset(request: PasswordResetRequest): Result<VerificationResponse> = runCatching {
        val response = client.post {
            url("$apiPath/request-password-reset")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.body()
    }

    /**
     * Confirm a password reset with a verification code and new password
     */
    suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest): Result<VerificationResponse> = runCatching {
        val response = client.post {
            url("$apiPath/confirm-password-reset")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.body()
    }
}
