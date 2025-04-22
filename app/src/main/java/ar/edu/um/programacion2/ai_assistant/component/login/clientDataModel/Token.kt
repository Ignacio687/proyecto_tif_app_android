package ar.edu.um.programacion2.computech.component.login.clientDataModel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Token(
    @SerialName("id_token")
    val token: String
)