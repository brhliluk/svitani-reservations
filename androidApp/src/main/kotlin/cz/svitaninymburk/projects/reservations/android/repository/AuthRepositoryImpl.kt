package cz.svitaninymburk.projects.reservations.android.repository

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.api.ApiError
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import androidx.core.content.edit
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse

class AuthRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : AuthRepository {
    override suspend fun login(email: String, password: String): Either<ApiError, AuthResponse> = either {
        val response = catch {
            httpClient.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
        }.mapLeft { ApiError("NetworkError", it.message ?: "Chyba sítě") }.bind()

        ensure(response.status.isSuccess()) {
            catch { response.body<ApiError>() }
                .getOrElse { ApiError("HttpError", "HTTP ${response.status.value}") }
        }

        val auth = catch { response.body<AuthResponse>() }
            .mapLeft { ApiError("ParseError", it.message ?: "Chyba parsování odpovědi") }
            .bind()

        prefs.edit {
            putString(KEY_ACCESS_TOKEN, auth.accessToken)
            putString(KEY_REFRESH_TOKEN, auth.refreshToken)
        }

        auth
    }

    override fun hasToken(): Boolean =
        prefs.getString(KEY_ACCESS_TOKEN, null) != null

    override fun clearTokens() {
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
        }
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
