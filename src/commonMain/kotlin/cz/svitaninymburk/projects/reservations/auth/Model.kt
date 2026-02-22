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
)

fun User.toDto() = UserDto(
    id = this.id,
    email = this.email,
    fullName = this.name + " " + this.surname,
    role = this.role,
)