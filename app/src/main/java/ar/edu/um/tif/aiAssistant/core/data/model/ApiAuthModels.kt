package ar.edu.um.tif.aiAssistant.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains all authentication-related models used in the application.
 */
object ApiAuthModels {
    @Serializable
    data class GoogleAuthRequest(
        val token: String
    )

    @Serializable
    data class EmailRegisterRequest(
        val email: String,
        val username: String,
        val password: String,
        val name: String? = null
    )

    @Serializable
    data class EmailLoginRequest(
        @SerialName("email_or_username")
        val emailOrUsername: String,
        val password: String
    )

    @Serializable
    data class EmailVerificationRequest(
        val code: String
    )

    @Serializable
    data class ResendVerificationRequest(
        val email: String
    )

    @Serializable
    data class PasswordResetRequest(
        val email: String
    )

    @Serializable
    data class PasswordResetConfirmRequest(
        val code: String,
        @SerialName("new_password")
        val newPassword: String
    )

    @Serializable
    data class AuthResponse(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("token_type")
        val tokenType: String = "bearer",
        @SerialName("user_id")
        val userId: String,
        val email: String,
        val name: String? = null,
        @SerialName("is_verified")
        val isVerified: Boolean
    )

    @Serializable
    data class VerificationResponse(
        val message: String,
        val verified: Boolean
    )
}
