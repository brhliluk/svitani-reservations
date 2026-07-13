package cz.svitaninymburk.projects.reservations.android.repository.auth

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.auth.UserDto

interface AuthRepository {
    suspend fun login(email: String, password: String): Either<RepositoryError, AuthResponse>
    suspend fun hasToken(): Boolean
    suspend fun clearTokens()
    suspend fun getUser(): UserDto?
}
