package cz.svitaninymburk.projects.reservations.auth

import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid


@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val surname: String,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: Uuid,
    val email: String,
    val fullName: String,
    val role: User.Role,
    val walletCode: String? = null,
)

fun User.toDto(walletCode: String? = null) = UserDto(
    id = this.id,
    email = this.email,
    fullName = this.name + " " + this.surname,
    role = this.role,
    walletCode = walletCode,
)
/**
 * Minimální délka hesla. Ve `shared`, protože ji musí znát obě strany —
 * formulář, aby nepustil odeslání, a `UserService.changePassword`, aby ji
 * vynutil. Dokud byla opsaná na čtyřech místech, mohly se rozejít a klient by
 * pustil heslo, které server odmítne.
 */
const val MIN_PASSWORD_LENGTH = 6
