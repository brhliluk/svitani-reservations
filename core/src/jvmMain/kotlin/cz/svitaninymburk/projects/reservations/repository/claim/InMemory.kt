package cz.svitaninymburk.projects.reservations.repository.claim

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant
import kotlin.uuid.Uuid

class InMemoryReservationClaimTokenRepository : ReservationClaimTokenRepository {
    private val tokens = ConcurrentHashMap<String, ReservationClaimToken>()

    override suspend fun save(token: ReservationClaimToken) {
        tokens[token.token] = token
    }

    override suspend fun findByToken(token: String): ReservationClaimToken? = tokens[token]

    override suspend fun findLatestUnusedFor(userId: Uuid): ReservationClaimToken? =
        tokens.values
            .filter { it.userId == userId && it.usedAt == null }
            .maxByOrNull { it.createdAt }

    /** Atomicky jako v databázi — dvě souběžná uplatnění nesmí projít obě. */
    override suspend fun markUsed(token: String, now: Instant, linkedCount: Int): Boolean {
        val current = tokens[token] ?: return false
        if (current.usedAt != null) return false
        return tokens.replace(token, current, current.copy(usedAt = now, linkedCount = linkedCount))
    }

    override suspend fun updateLinkedCount(token: String, linkedCount: Int) {
        tokens.computeIfPresent(token) { _, current -> current.copy(linkedCount = linkedCount) }
    }

    override suspend fun deleteUnusedFor(userId: Uuid) {
        tokens.values.removeIf { it.userId == userId && it.usedAt == null }
    }
}
