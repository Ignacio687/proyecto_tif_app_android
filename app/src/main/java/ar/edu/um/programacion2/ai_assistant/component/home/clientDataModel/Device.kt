package ar.edu.um.programacion2.computech.component.home.clientDataModel

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: Int,
    val supplierForeignId: Int,
    val supplier: String,
    val code: String,
    val name: String,
    val description: String,
    val basePrice: Double,
    val currency: String
)