package cz.svitaninymburk.projects.reservations.user

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant


@Serializable
sealed class User {
    abstract val id: String
    abstract val email: String
    abstract val name: String
    abstract val surname: String
    abstract val role: Role

    @Serializable
    data class Google(
        override val id: String,
        override val email: String,
        override val name: String,
        override val surname: String,
        override val role: Role,
        val googleSub: String
    ): User()

    @Serializable
    data class Email(
        override val id: String,
        override val email: String,
        override val name: String,
        override val surname: String,
        override val role: Role,
        val passwordHash: String,
        val passwordResetToken: String? = null,
        val passwordResetTokenExpiresAt: LocalDateTime? = null,
    ): User() {
        fun toGoogle(googleSub: String): Google {
            return Google(id = id, email = email, name = name, surname = surname, role = role, googleSub = googleSub)
        }
    }

    @Serializable
    enum class Role { USER, ADMIN }
}

@Serializable
data class UpdateUserRequest(
    val userId: String,
    val user: User,
)
