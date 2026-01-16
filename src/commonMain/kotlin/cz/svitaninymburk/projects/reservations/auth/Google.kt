package cz.svitaninymburk.projects.reservations.auth

import kotlinx.serialization.Serializable

@Serializable
data class GoogleLoginRequest(
    val idToken: String
)