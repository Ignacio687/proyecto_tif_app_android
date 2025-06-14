package ar.edu.um.tif.aiAssistant.core.data.model

data class GoogleAuthRequest(
    val token: String
)

data class EmailRegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val name: String? = null
)

data class EmailLoginRequest(
    val email_or_username: String,
    val password: String
)

data class EmailVerificationRequest(
    val code: String
)

data class ResendVerificationRequest(
    val email: String
)

data class PasswordResetRequest(
    val email: String
)

data class PasswordResetConfirmRequest(
    val code: String,
    val new_password: String
)

data class AuthResponse(
    val access_token: String,
    val token_type: String = "bearer",
    val user_id: String,
    val email: String,
    val name: String? = null,
    val is_verified: Boolean
)

data class UserRequest(
    val user_req: String
)

data class Skill(
    val name: String,
    val action: String,
    val params: Map<String, Any>
)

data class ServerResponse(
    val server_reply: String,
    val app_params: List<Map<String, Boolean>>? = null,
    val skills: List<Skill>? = null
)
