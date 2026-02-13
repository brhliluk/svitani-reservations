package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass


@Serializable @SerialName("auth") sealed interface AuthError : AppError {
    @Serializable @SerialName("google_login") sealed interface LoginWithGoogle : AuthError
    @Serializable @SerialName("register") sealed interface Register : AuthError
    @Serializable @SerialName("email_login") sealed interface LoginWithEmail : AuthError
    @Serializable @SerialName("refresh_token") sealed interface RefreshToken : AuthError
    @Serializable @SerialName("get_current") sealed interface GetCurrentUser : AuthError
    @Serializable @SerialName("request_password_reset") sealed interface RequestPasswordReset : AuthError
    @Serializable @SerialName("reset_password") sealed interface ResetPassword : AuthError

    @Serializable data object InvalidGoogleToken : LoginWithGoogle
    @Serializable data class LoggedInWithAnotherProvider(val userClass: KClass<User>) : LoginWithEmail, RequestPasswordReset
    @Serializable data object InvalidCredentials : LoginWithEmail
    @Serializable data object UserAlreadyExists : Register
    @Serializable data object InvalidToken : RefreshToken
    @Serializable data object TokenExpired : RefreshToken, ResetPassword
    @Serializable data object UserNotFound : RefreshToken, GetCurrentUser, RequestPasswordReset, ResetPassword
    @Serializable data object ApplicationCallLost: GetCurrentUser
    @Serializable data object NoJwtPrincipal: GetCurrentUser
    @Serializable data object NoIdInPrincipal: GetCurrentUser
}

val AuthError.localizedMessage: String get() = when (this) {
    is AuthError.InvalidCredentials -> "Neplatné přihlašovací údaje"
    is AuthError.LoggedInWithAnotherProvider -> "Přihlášení jinou metodou: ${userClass.simpleName}"
    is AuthError.UserAlreadyExists -> "Účet již existuje"
    is AuthError.InvalidGoogleToken -> "Neplatný Google Token"
    is AuthError.InvalidToken -> "Neplatný token"
    is AuthError.TokenExpired -> "Token vypršel"
    is AuthError.UserNotFound -> "Uživatel nenalezen"
    is AuthError.ApplicationCallLost -> "Chyba při zpracování požadavku"
    is AuthError.NoJwtPrincipal, is AuthError.NoIdInPrincipal -> "Uživatel není přihlášen"
}
