package ar.edu.um.tif.aiAssistant.core.data.remote

import ar.edu.um.tif.aiAssistant.core.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KtorApiClient @Inject constructor(
    private val client: HttpClient
) {
    // Authentication endpoints

    suspend fun authenticateWithGoogle(request: GoogleAuthRequest): Result<AuthResponse> = runCatching {
        val response = client.post {
            url("/api/v1/auth/google")
            setBody(request)
        }
        response.body()
    }

    suspend fun verifyToken(token: String): Result<Map<String, Any>> = runCatching {
        val response = client.post {
            url("/api/v1/auth/verify-token")
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        response.body()
    }

    suspend fun registerWithEmail(request: EmailRegisterRequest): Result<Map<String, Any>> = runCatching {
        val response = client.post {
            url("/api/v1/auth/register")
            setBody(request)
        }
        response.body()
    }

    suspend fun loginWithEmail(request: EmailLoginRequest): Result<AuthResponse> = runCatching {
        val response = client.post {
            url("/api/v1/auth/login")
            setBody(request)
        }
        response.body()
    }

    suspend fun verifyEmail(request: EmailVerificationRequest): Result<Map<String, Any>> = runCatching {
        val response = client.post {
            url("/api/v1/auth/verify-email")
            setBody(request)
        }
        response.body()
    }

    suspend fun resendVerificationCode(request: ResendVerificationRequest): Result<Map<String, Any>> = runCatching {
        val response = client.post {
            url("/api/v1/auth/resend-verification")
            setBody(request)
        }
        response.body()
    }

    suspend fun requestPasswordReset(request: PasswordResetRequest): Result<Map<String, Any>> = runCatching {
        val response = client.post {
            url("/api/v1/auth/request-password-reset")
            setBody(request)
        }
        response.body()
    }

    suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest): Result<Map<String, Any>> = runCatching {
        val response = client.post {
            url("/api/v1/auth/confirm-password-reset")
            setBody(request)
        }
        response.body()
    }

    // Assistant endpoints

    suspend fun sendUserRequest(token: String, request: UserRequest): Result<ServerResponse> = runCatching {
        val response = client.post {
            url("/api/v1/assistant")
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            setBody(request)
        }
        response.body()
    }

    suspend fun getConversationHistory(token: String, page: Int = 1, pageSize: Int = 10): Result<Map<String, Any>> = runCatching {
        val response = client.get {
            url("/api/v1/conversations")
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            parameter("page", page)
            parameter("page_size", pageSize)
        }
        response.body()
    }
}
