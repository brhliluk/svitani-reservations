package cz.svitaninymburk.projects.reservations.android.repository.auth

import android.content.SharedPreferences
import androidx.core.content.edit
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
    private val prefs: SharedPreferences,
) : AuthRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun login(email: String, password: String): Either<RepositoryError, AuthResponse> =
        httpClient.postJson<LoginRequest, AuthResponse>("/api/v1/auth/login", LoginRequest(email, password))
            .onRight { auth ->
                prefs.edit {
                    putString(KEY_ACCESS_TOKEN, auth.accessToken)
                    putString(KEY_REFRESH_TOKEN, auth.refreshToken)
                    putString(KEY_USER, json.encodeToString(auth.user))
                }
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
