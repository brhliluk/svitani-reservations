package cz.svitaninymburk.projects.reservations.android.repository

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.api.ApiError
import cz.svitaninymburk.projects.reservations.auth.AuthResponse

interface AuthRepository {
    suspend fun login(email: String, password: String): Either<ApiError, AuthResponse>
    fun hasToken(): Boolean
    fun clearTokens()
}
