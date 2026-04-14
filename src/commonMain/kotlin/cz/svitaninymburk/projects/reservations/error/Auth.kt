package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.i18n.ErrorStrings
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

fun AuthError.localizedMessage(strings: ErrorStrings): String = when (this) {
    is AuthError.InvalidCredentials -> strings.errorInvalidCredentials
    is AuthError.LoggedInWithAnotherProvider -> strings.errorLoggedInWithAnotherProvider(userClass.simpleName)
    is AuthError.UserAlreadyExists -> strings.errorUserAlreadyExists
    is AuthError.InvalidGoogleToken -> strings.errorInvalidGoogleToken
    is AuthError.InvalidToken -> strings.errorInvalidToken
    is AuthError.TokenExpired -> strings.errorTokenExpired
    is AuthError.UserNotFound -> strings.errorUserNotFound
    is AuthError.ApplicationCallLost -> strings.errorProcessingRequest
    is AuthError.NoJwtPrincipal, is AuthError.NoIdInPrincipal -> strings.errorNotLoggedIn
}
