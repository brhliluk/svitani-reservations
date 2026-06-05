package cz.svitaninymburk.projects.reservations.auth

import kotlin.time.Instant
import kotlin.uuid.Uuid


data class RefreshToken(
    val token: String,
    val userId: Uuid,
    val expiresAt: Instant,
)
