package cz.svitaninymburk.projects.reservations.repository.claim

import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.util.dbQuery
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

object ReservationClaimTokensTable : Table("reservation_claim_tokens") {
    val token = varchar("token", 255)
    val userId = reference("user_id", UsersTable.id, onDelete = ReferenceOption.CASCADE)

    /** Adresa, na kterou odkaz odešel. Viz [ReservationClaimToken.email]. */
    val email = varchar("email", 255)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val linkedCount = integer("linked_count").nullable()

    override val primaryKey = PrimaryKey(token)
}

class ExposedReservationClaimTokenRepository : ReservationClaimTokenRepository {

    override suspend fun save(token: ReservationClaimToken): Unit = dbQuery {
        ReservationClaimTokensTable.insert {
            it[this.token] = token.token
            it[this.userId] = token.userId
            it[this.email] = token.email
            it[this.createdAt] = token.createdAt
            it[this.expiresAt] = token.expiresAt
            it[this.usedAt] = token.usedAt
            it[this.linkedCount] = token.linkedCount
        }
    }

    override suspend fun findByToken(token: String): ReservationClaimToken? = dbQuery {
        ReservationClaimTokensTable.selectAll()
            .where { ReservationClaimTokensTable.token eq token }
            .map { it.toClaimToken() }
            .singleOrNull()
    }

    override suspend fun findLatestUnusedFor(userId: Uuid): ReservationClaimToken? = dbQuery {
        ReservationClaimTokensTable.selectAll()
            .where { (ReservationClaimTokensTable.userId eq userId) and ReservationClaimTokensTable.usedAt.isNull() }
            .orderBy(ReservationClaimTokensTable.createdAt, SortOrder.DESC)
            .limit(1)
            .map { it.toClaimToken() }
            .singleOrNull()
    }

    override suspend fun markUsed(token: String, now: Instant, linkedCount: Int): Boolean = dbQuery {
        ReservationClaimTokensTable.update({
            (ReservationClaimTokensTable.token eq token) and ReservationClaimTokensTable.usedAt.isNull()
        }) {
            it[usedAt] = now
            it[this.linkedCount] = linkedCount
        } > 0
    }

    override suspend fun updateLinkedCount(token: String, linkedCount: Int): Unit = dbQuery {
        ReservationClaimTokensTable.update({ ReservationClaimTokensTable.token eq token }) {
            it[this.linkedCount] = linkedCount
        }
    }

    override suspend fun deleteUnusedFor(userId: Uuid): Unit = dbQuery {
        ReservationClaimTokensTable.deleteWhere {
            (ReservationClaimTokensTable.userId eq userId) and ReservationClaimTokensTable.usedAt.isNull()
        }
    }
}

private fun ResultRow.toClaimToken(): ReservationClaimToken = ReservationClaimToken(
    token = this[ReservationClaimTokensTable.token],
    userId = this[ReservationClaimTokensTable.userId],
    email = this[ReservationClaimTokensTable.email],
    createdAt = this[ReservationClaimTokensTable.createdAt],
    expiresAt = this[ReservationClaimTokensTable.expiresAt],
    usedAt = this[ReservationClaimTokensTable.usedAt],
    linkedCount = this[ReservationClaimTokensTable.linkedCount],
)
