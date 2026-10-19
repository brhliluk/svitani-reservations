package cz.svitaninymburk.projects.reservations.api

import arrow.core.Either
import arrow.core.right
import cz.svitaninymburk.projects.reservations.AppJson
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.auth.RegisterRequest
import cz.svitaninymburk.projects.reservations.auth.UserDto
import cz.svitaninymburk.projects.reservations.error.AuthError
import cz.svitaninymburk.projects.reservations.plugins.mobilePublicAuthRoutes
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.service.RefreshTokenServiceInterface
import cz.svitaninymburk.projects.reservations.user.User
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

class MobileApiSmokeTest {

    private val fakeUserId = Uuid.parse("00000000-0000-0000-0000-000000000001")

    private val fakeAuthResponse = AuthResponse(
        accessToken = "fake-access-token",
        refreshToken = "fake-refresh-token",
        user = UserDto(
            id = fakeUserId,
            email = "a@b.cz",
            fullName = "Test User",
            role = User.Role.USER,
            walletCode = null,
        ),
    )

    private val fakeAuthService = object : AuthServiceInterface {
        override suspend fun login(request: LoginRequest): Either<AuthError.LoginWithEmail, AuthResponse> =
            fakeAuthResponse.right()

        override suspend fun getCurrentUser(): Either<AuthError.GetCurrentUser, User> = TODO()
        override suspend fun getMyWalletCode(): Either<AuthError.GetCurrentUser, String> = TODO()
        override suspend fun logout(): Either<AuthError, Unit> = TODO()
        override suspend fun loginWithGoogle(token: String): Either<AuthError.LoginWithGoogle, AuthResponse> = TODO()
        override suspend fun register(request: RegisterRequest): Either<AuthError.Register, AuthResponse> = TODO()
        override suspend fun requestPasswordReset(email: String): Either<AuthError, Unit> = TODO()
        override suspend fun resetPassword(token: String, newPassword: String): Either<AuthError, Unit> = TODO()
    }

    private val fakeRefreshService = object : RefreshTokenServiceInterface {
        override suspend fun refreshToken(token: String): Either<AuthError.RefreshToken, String> = TODO()
    }

    @Test
    fun `POST login returns 200 with accessToken`() = testApplication {
        application {
            install(ContentNegotiation) { json(AppJson) }
            install(Koin) {
                modules(module {
                    single<AuthServiceInterface> { fakeAuthService }
                    single<RefreshTokenServiceInterface> { fakeRefreshService }
                })
            }
            routing {
                mobilePublicAuthRoutes()
            }
        }

        val response = client.post("/api/v1/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"a@b.cz","password":"pw"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "accessToken")
    }
}
