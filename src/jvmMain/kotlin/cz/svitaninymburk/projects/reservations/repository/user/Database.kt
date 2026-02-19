package cz.svitaninymburk.projects.reservations.repository.user

import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.util.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

// 1. DEFINICE TABULKY
object UsersTable : Table("users") {
    val id = uuid("id").autoGenerate()
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255)
    val surname = varchar("surname", 255)

    val role = enumerationByName("role", 20, User.Role::class)

    val googleSub = varchar("google_sub", 255).nullable()
    val passwordHash = varchar("password_hash", 255).nullable()
    val passwordResetToken = varchar("password_reset_token", 255).nullable()
    val passwordResetTokenExpiresAt = datetime("password_reset_token_expires_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

class ExposedUserRepository : UserRepository {

    override suspend fun findByEmail(email: String): User? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.email eq email }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun findById(id: Uuid): User? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.id eq id }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun findByResetToken(token: String): User.Email? = dbQuery {
        val user = UsersTable.selectAll()
            .where { UsersTable.passwordResetToken eq token }
            .map { it.toUser() }
            .singleOrNull()

        user as? User.Email
    }

    override suspend fun create(user: User): User = dbQuery {
        UsersTable.insert { row ->
            row[id] = user.id
            row[email] = user.email
            row[name] = user.name
            row[surname] = user.surname
            row[role] = user.role

            when (user) {
                is User.Email -> {
                    row[passwordHash] = user.passwordHash
                    row[passwordResetToken] = user.passwordResetToken
                    row[passwordResetTokenExpiresAt] = user.passwordResetTokenExpiresAt
                }
                is User.Google -> {
                    row[googleSub] = user.googleSub
                }
            }
        }
        user
    }

    override suspend fun update(userId: Uuid, user: User): User = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) { row ->
            row[email] = user.email
            row[name] = user.name
            row[surname] = user.surname
            row[role] = user.role

            when (user) {
                is User.Email -> {
                    row[passwordHash] = user.passwordHash
                    row[passwordResetToken] = user.passwordResetToken
                    row[passwordResetTokenExpiresAt] = user.passwordResetTokenExpiresAt
                }
                is User.Google -> {
                    row[googleSub] = user.googleSub
                }
            }
        }
        user
    }

    override suspend fun linkGoogleAccount(userId: Uuid, googleSub: String): User.Google = dbQuery {
        val existingUser = findById(userId) as? User.Email ?: error("Uživatel nenalezen nebo to není klasický Email účet.")

        UsersTable.update({ UsersTable.id eq userId }) {
            it[this.googleSub] = googleSub
        }

        existingUser.toGoogle(googleSub)
    }
}

fun ResultRow.toUser(): User {
    val pHash = this[UsersTable.passwordHash]
    val gSub = this[UsersTable.googleSub]

    return if (pHash != null) {
        User.Email(
            id = this[UsersTable.id],
            email = this[UsersTable.email],
            name = this[UsersTable.name],
            surname = this[UsersTable.surname],
            role = this[UsersTable.role],
            passwordHash = pHash,
            passwordResetToken = this[UsersTable.passwordResetToken],
            passwordResetTokenExpiresAt = this[UsersTable.passwordResetTokenExpiresAt]
        )
    } else if (gSub != null) {
        User.Google(
            id = this[UsersTable.id],
            email = this[UsersTable.email],
            name = this[UsersTable.name],
            surname = this[UsersTable.surname],
            role = this[UsersTable.role],
            googleSub = gSub
        )
    } else {
        error("Uživatel v databázi (ID: ${this[UsersTable.id]}) nemá ani heslo, ani Google Sub!")
    }
}
