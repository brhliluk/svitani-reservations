package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.auth.GoogleAuthService
import cz.svitaninymburk.projects.reservations.auth.HashingService
import cz.svitaninymburk.projects.reservations.auth.JwtTokenService
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.auth.RegisterRequest
import cz.svitaninymburk.projects.reservations.auth.toDto
import cz.svitaninymburk.projects.reservations.error.AuthError
import cz.svitaninymburk.projects.reservations.repository.auth.RefreshTokenRepository
import cz.svitaninymburk.projects.reservations.repository.user.UserRepository
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.util.currentCall
import io.ktor.http.Cookie
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.util.date.GMTDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mindrot.jbcrypt.BCrypt
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid


class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenService: RefreshTokenService,
    private val googleAuth: GoogleAuthService,
    private val emailService: EmailService,
    private val tokenService: JwtTokenService,
    private val hashingService: HashingService,
    private val walletService: WalletService,
): AuthServiceInterface {

    override suspend fun loginWithGoogle(token: String): Either<AuthError.LoginWithGoogle, AuthResponse> = either {
        val googleUser = ensureNotNull(googleAuth.verifyToken(token)) {
            AuthError.InvalidGoogleToken
        }

        var user = userRepository.findByEmail(googleUser.email)

        if (user == null) {
            user = userRepository.create(
                User.Google(
                    id = Uuid.random(),
                    email = googleUser.email,
                    name = googleUser.name,
                    surname = googleUser.surname,
                    googleSub = googleUser.googleSub,
                    role = User.Role.USER,
                )
            )
        } else {
            if (user is User.Email) {
                user = userRepository.linkGoogleAccount(user.id, googleUser.googleSub)
            }
        }

        val accessToken = tokenService.generateToken(user)
        val refreshToken = refreshTokenService.getToken(user.id)
        val wallet = walletService.findOrCreateForRegisteredUser(user.id, user.email)

        AuthResponse(accessToken, refreshToken, user.toDto(walletCode = wallet.code))
    }

    override suspend fun register(request: RegisterRequest): Either<AuthError.Register, AuthResponse> = either {

        val existingUser = userRepository.findByEmail(request.email)
        ensure(existingUser == null) { AuthError.UserAlreadyExists }

        val hash = hashingService.generateSaltedHash(request.password)

        val newUser = userRepository.create(
            User.Email(
                id = Uuid.random(),
                email = request.email,
                name = request.name,
                surname = request.surname,
                role = User.Role.USER,
                passwordHash = hash,
            )
        )

        val token = tokenService.generateToken(newUser)
        val refreshToken = refreshTokenService.getToken(newUser.id)
        val wallet = walletService.findOrCreateForRegisteredUser(newUser.id, newUser.email)

        AuthResponse(token, refreshToken, newUser.toDto(walletCode = wallet.code))
    }

    override suspend fun login(request: LoginRequest): Either<AuthError.LoginWithEmail, AuthResponse> = either {

        val user = userRepository.findByEmail(request.email)

        ensureNotNull(user) { AuthError.InvalidCredentials }

        ensure(user is User.Email) { AuthError.LoggedInWithAnotherProvider(@Suppress("UNCHECKED_CAST") (User.Google::class as KClass<User>)) }

        val isValid = hashingService.verify(request.password, user.passwordHash)

        ensure(isValid) { AuthError.InvalidCredentials }

        val token = tokenService.generateToken(user)
        val refreshToken = refreshTokenService.getToken(user.id)
        val wallet = walletService.findOrCreateForRegisteredUser(user.id, user.email)

        val call = currentCall() ?: throw IllegalStateException("Call context is missing")
        call.response.cookies.append(
            Cookie(
                name = "auth_token",
                value = token,
                httpOnly = true,
                secure = true,
                path = "/",
                maxAge = 7.days.inWholeSeconds.toInt(),
            )
        )

        AuthResponse(token, refreshToken, user.toDto(walletCode = wallet.code))
    }

    override suspend fun logout(): Either<AuthError, Unit> = either {
        val call = currentCall()
        call?.response?.cookies?.append(
            Cookie(
                name = "auth_token",
                value = "",
                path = "/",
                maxAge = 0,
                expires = GMTDate.START
            )
        )
        Unit
    }

    override suspend fun getCurrentUser(): Either<AuthError.GetCurrentUser, User> = either {
        val call = ensureNotNull(currentCall()) { AuthError.ApplicationCallLost }
        val principal = ensureNotNull(call.principal<JWTPrincipal>()) { AuthError.NoJwtPrincipal }

        val userId = ensureNotNull(Uuid.parse(principal.payload.getClaim("id").asString())) { AuthError.NoIdInPrincipal }

        userRepository.findById(userId) ?: raise(AuthError.UserNotFound)
    }

    override suspend fun requestPasswordReset(email: String): Either<AuthError.RequestPasswordReset, Unit> = either {
        val user = ensureNotNull(userRepository.findByEmail(email)) { AuthError.UserNotFound }
        ensure(user is User.Email) { AuthError.LoggedInWithAnotherProvider(@Suppress("UNCHECKED_CAST") (User.Google::class as KClass<User>)) }

        val token = Uuid.random().toString()

        val updatedUser = user.copy(
            passwordResetToken = token,
            passwordResetTokenExpiresAt = Clock.System.now().plus(1.hours).toLocalDateTime(TimeZone.currentSystemDefault())
        )
        userRepository.update(user.id, updatedUser)
        emailService.sendPasswordResetEmail(user.email, token)
    }

    override suspend fun resetPassword(token: String, newPassword: String): Either<AuthError.ResetPassword, Unit> = either {
        val user = ensureNotNull(userRepository.findByResetToken(token)) { AuthError.UserNotFound }

        val passwordResetTokenExpiresAt = user.passwordResetTokenExpiresAt
        if (passwordResetTokenExpiresAt == null || passwordResetTokenExpiresAt < Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) {
            raise(AuthError.TokenExpired)
        }

        val salt = BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(newPassword, salt)

        val updatedUser = user.copy(
            passwordHash = hashedPassword,
            passwordResetToken = null,
            passwordResetTokenExpiresAt = null
        )
        userRepository.update(user.id, updatedUser)
    }

    override suspend fun getMyWalletCode(): Either<AuthError.GetCurrentUser, String> = either {
        val call = ensureNotNull(currentCall()) { AuthError.ApplicationCallLost }
        val principal = ensureNotNull(call.principal<JWTPrincipal>()) { AuthError.NoJwtPrincipal }
        val userId = ensureNotNull(Uuid.parse(principal.payload.getClaim("id").asString())) { AuthError.NoIdInPrincipal }
        val user = userRepository.findById(userId) ?: raise(AuthError.UserNotFound)
        walletService.findOrCreateForRegisteredUser(userId, user.email).code
    }
}

class AuthRefreshTokenService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenService: JwtTokenService,
): RefreshTokenServiceInterface {
    override suspend fun refreshToken(token: String): Either<AuthError.RefreshToken, String> = either {
        val storedToken = ensureNotNull(refreshTokenRepository.findByToken(token)) { AuthError.InvalidToken }

        if (storedToken.expiresAt < Clock.System.now()) {
            refreshTokenRepository.deleteByToken(token)
            raise(AuthError.TokenExpired)
        }

        val user = userRepository.findById(storedToken.userId)
        ensureNotNull(user) { AuthError.UserNotFound }

        tokenService.generateToken(user)
    }
}