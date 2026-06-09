package cz.svitaninymburk.projects.reservations.android.repository.auth

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.api.ApiError
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.auth.UserDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import androidx.core.content.edit
import arrow.core.raise.ensure
import kotlinx.serialization.json.Json

class AuthRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : AuthRepository {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun login(email: String, password: String): Either<RepositoryError, AuthResponse> = either {
        val response = catch {
            httpClient.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
        }.mapLeft { RepositoryError.Network }.bind()

        ensure(response.status.isSuccess()) {
            catch { response.body<ApiError>() }
                .map { RepositoryError.Server(it.code, it.message) }
                .getOrElse { RepositoryError.Http(response.status.value) }
        }

        val auth = catch { response.body<AuthResponse>() }
            .mapLeft { RepositoryError.Parse }
            .bind()

        prefs.edit {
            putString(KEY_ACCESS_TOKEN, auth.accessToken)
            putString(KEY_REFRESH_TOKEN, auth.refreshToken)
            putString(KEY_USER, json.encodeToString(auth.user))
        }

        auth
    }

    override fun hasToken(): Boolean =
        prefs.getString(KEY_ACCESS_TOKEN, null) != null

    override fun clearTokens() {
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER)
        }
    }

    override fun getUser(): UserDto? =
        prefs.getString(KEY_USER, null)?.let {
            catch { json.decodeFromString<UserDto>(it) }.getOrNull()
        }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER = "user_dto"
    }
}
