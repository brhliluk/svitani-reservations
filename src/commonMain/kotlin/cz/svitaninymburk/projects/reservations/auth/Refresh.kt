package cz.svitaninymburk.projects.reservations.auth

import kotlin.time.Instant


data class RefreshToken(
    val token: String,
    val userId: String,
    val expiresAt: Instant,
)
