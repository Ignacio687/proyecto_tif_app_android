package ar.edu.um.programacion2.ai_assistant.component.home.clientDataModel

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: Int,
    val login: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val imageUrl: String,
    val activated: Boolean,
    val langKey: String,
    val createdBy: String,
    val createdDate: String?,
    val lastModifiedBy: String,
    val lastModifiedDate: String?,
    val authorities: List<String>
)