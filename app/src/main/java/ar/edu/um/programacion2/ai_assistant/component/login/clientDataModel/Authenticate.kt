package ar.edu.um.programacion2.ai_assistant.component.login.clientDataModel

import kotlinx.serialization.Serializable

@Serializable
data class Authenticate(
    val username: String,
    val password: String,
    val rememberMe: Boolean
)
