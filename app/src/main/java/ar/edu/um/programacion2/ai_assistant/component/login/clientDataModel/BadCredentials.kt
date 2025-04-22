package ar.edu.um.programacion2.computech.component.login.clientDataModel

import kotlinx.serialization.Serializable

@Serializable
data class BadCredentials(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val message: String,
    val path: String
)
