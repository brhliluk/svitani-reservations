package cz.svitaninymburk.projects.reservations.android.repository.auth

import arrow.core.Either
import arrow.core.Either.Companion.catch
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.postJson
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.auth.UserDto
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

class AuthRepositoryImpl(
    private val httpClient: HttpClient,
    private val dataSource: AuthLocalDataSource,
) : AuthRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun login(email: String, password: String): Either<RepositoryError, AuthResponse> =
        httpClient.postJson<LoginRequest, AuthResponse>("/api/v1/auth/login", LoginRequest(email, password))
            .onRight { auth ->
                dataSource.saveAuth(
                    accessToken = auth.accessToken,
                    refreshToken = auth.refreshToken,
                    userJson = json.encodeToString(auth.user)
                )
            }

    override suspend fun hasToken(): Boolean =
        dataSource.getAccessToken() != null

    override suspend fun clearTokens() {
        dataSource.clearAuth()
    }

    override suspend fun getUser(): UserDto? =
        dataSource.getUserJson()?.let {
            catch { json.decodeFromString<UserDto>(it) }.getOrNull()
        }
}
