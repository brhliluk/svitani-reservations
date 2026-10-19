package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.left
import cz.svitaninymburk.projects.reservations.auth.BCryptHashingService
import cz.svitaninymburk.projects.reservations.auth.GoogleAuthService
import cz.svitaninymburk.projects.reservations.auth.JwtTokenService
import cz.svitaninymburk.projects.reservations.error.AuthError
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.repository.auth.InMemoryRefreshTokenRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Když e-mail s odkazem neodejde, nesmí uživatel ani admin vidět „e-mail odeslán“. */
class PasswordResetRequestSpec {

    private val user = User.Email(
        id = Uuid.random(),
        email = "rodic@test.cz",
        name = "Jana",
        surname = "Nováková",
        role = User.Role.USER,
        passwordHash = "hash",
    )

    private suspend fun service(emailService: EmailService): AuthService {
        val users = InMemoryUserRepository().also { it.create(user) }
        val tokens = JwtTokenService(secret = "secret", issuer = "issuer", audience = "audience")
        return AuthService(
            userRepository = users,
            refreshTokenService = RefreshTokenService(InMemoryRefreshTokenRepository(), tokens),
            googleAuth = GoogleAuthService(),
            emailService = emailService,
            tokenService = tokens,
            hashingService = BCryptHashingService(),
            walletService = WalletService(InMemoryWalletRepository()),
        )
    }

    @Test
    fun `failed reset email is reported as an error`() = runBlocking {
        val failing = object : EmailService by ConsoleEmailService() {
            override suspend fun sendPasswordResetEmail(toEmail: String, resetToken: String): Either<EmailError.SendPasswordReset, Unit> =
                EmailError.SendPasswordResetFailed("451 try again later").left()
        }

        val result = service(failing).requestPasswordReset(user.email)

        assertEquals(AuthError.PasswordResetEmailNotSent, result.leftOrNull())
    }

    @Test
    fun `sent reset email is a success`() = runBlocking {
        val result = service(ConsoleEmailService()).requestPasswordReset(user.email)

        assertTrue(result.isRight(), "Expected Right but got $result")
    }
}
