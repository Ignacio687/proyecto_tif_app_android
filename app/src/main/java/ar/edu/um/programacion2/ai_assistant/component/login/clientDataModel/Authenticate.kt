package ar.edu.um.programacion2.computech.component.login.clientDataModel

import kotlinx.serialization.Serializable

@Serializable
data class Authenticate(
    val username: String,
    val password: String,
    val rememberMe: Boolean
)
