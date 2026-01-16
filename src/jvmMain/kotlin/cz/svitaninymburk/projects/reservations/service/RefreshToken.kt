package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.auth.RefreshToken
import cz.svitaninymburk.projects.reservations.repository.auth.RefreshTokenRepository
import cz.svitaninymburk.projects.reservations.auth.JwtTokenService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days


class RefreshTokenService(
    val refreshTokenRepository: RefreshTokenRepository,
    private val tokenService: JwtTokenService,
) {
    suspend fun getToken(userId: String): String {
        val refreshTokenString = tokenService.generateRefreshToken()

        refreshTokenRepository.save(
            RefreshToken(
                token = refreshTokenString,
                userId = userId,
                expiresAt = Clock.System.now() + 30.days
            )
        )
        return refreshTokenString
    }
}