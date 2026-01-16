package cz.svitaninymburk.projects.reservations.repository.auth

import cz.svitaninymburk.projects.reservations.auth.RefreshToken
import java.util.concurrent.ConcurrentHashMap


class InMemoryRefreshTokenRepository : RefreshTokenRepository {
    private val tokens = ConcurrentHashMap<String, RefreshToken>()

    override suspend fun save(token: RefreshToken) {
        tokens[token.token] = token
    }

    override suspend fun findByToken(token: String): RefreshToken? {
        return tokens[token]
    }

    override suspend fun deleteByToken(token: String) {
        tokens.remove(token)
    }

    override suspend fun deleteByUserId(userId: String) {
        tokens.values.removeIf { it.userId == userId }
    }
}