package cz.svitaninymburk.projects.reservations.android.repository

import android.content.SharedPreferences
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.api.ApiError
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import androidx.core.content.edit

class AuthRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Either<ApiError, AuthResponse> =
        try {
            val response: HttpResponse = httpClient.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
            if (response.status.isSuccess()) {
                val auth = response.body<AuthResponse>()
                prefs.edit {
                    putString(KEY_ACCESS_TOKEN, auth.accessToken)
                    putString(KEY_REFRESH_TOKEN, auth.refreshToken)
                }
                Either.Right(auth)
            } else {
                try {
                    Either.Left(response.body<ApiError>())
                } catch (e: Exception) {
                    Either.Left(ApiError("HttpError", "HTTP ${response.status.value}"))
                }
            }
        } catch (e: Exception) {
            Either.Left(ApiError("NetworkError", e.message ?: "Chyba sítě"))
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
