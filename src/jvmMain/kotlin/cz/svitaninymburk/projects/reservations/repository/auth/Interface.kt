package cz.svitaninymburk.projects.reservations.repository.auth

import cz.svitaninymburk.projects.reservations.auth.RefreshToken

interface RefreshTokenRepository {
    suspend fun save(token: RefreshToken)
    suspend fun findByToken(token: String): RefreshToken?
    suspend fun deleteByToken(token: String)
    suspend fun deleteByUserId(userId: String)
}
