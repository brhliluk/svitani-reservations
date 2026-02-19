package cz.svitaninymburk.projects.reservations.repository.auth

import cz.svitaninymburk.projects.reservations.auth.RefreshToken
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.util.dbQuery
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid


// 1. ZDE JE TA TABULKA (Mapování na SQL)
object RefreshTokensTable : Table("refresh_tokens") {
    val token = varchar("token", 255)
    val userId = reference("user_id", UsersTable.id)
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(token)
}

class ExposedRefreshTokenRepository : RefreshTokenRepository {
    override suspend fun save(token: RefreshToken): Unit = dbQuery {
        RefreshTokensTable.insert {
            it[this.token] = token.token
            it[this.userId] = token.userId
            it[this.expiresAt] = token.expiresAt
        }
    }

    override suspend fun findByToken(token: String): RefreshToken? = dbQuery {
        RefreshTokensTable
            .selectAll()
            .where { RefreshTokensTable.token eq token }
            .map { row ->
                RefreshToken(
                    token = row[RefreshTokensTable.token],
                    userId = row[RefreshTokensTable.userId],
                    expiresAt = row[RefreshTokensTable.expiresAt]
                )
            }
            .singleOrNull()
    }

    override suspend fun deleteByToken(token: String): Unit = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.token eq token }
    }

    override suspend fun deleteByUserId(userId: Uuid): Unit = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
    }
}
