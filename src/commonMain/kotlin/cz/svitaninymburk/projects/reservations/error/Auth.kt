package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.user.User
import kotlin.reflect.KClass


sealed interface AuthError : AppError {
    sealed interface LoginWithGoogle : AuthError
    sealed interface Register : AuthError
    sealed interface LoginWithEmail : AuthError
    sealed interface RefreshToken : AuthError

    data object InvalidGoogleToken : LoginWithGoogle
    data class LoggedInWithAnotherProvider(val userClass: KClass<User>) : LoginWithEmail
    data object InvalidCredentials : LoginWithEmail
    data object UserAlreadyExists : Register
    data object InvalidToken : RefreshToken
    data object TokenExpired : RefreshToken
    data object UserNotFound : RefreshToken
}

val AuthError.localizedMessage: String get() = when (this) {
    is AuthError.InvalidCredentials -> "Neplatné přihlašovací údaje"
    is AuthError.LoggedInWithAnotherProvider -> "Přihlášení jinou metodou: ${userClass.simpleName}"
    is AuthError.UserAlreadyExists -> "Účet již existuje"
    is AuthError.InvalidGoogleToken -> "Neplatný Google Token"
    is AuthError.InvalidToken -> "Neplatný token"
    is AuthError.TokenExpired -> "Token vypršel"
    is AuthError.UserNotFound -> "Uživatel nenalezen"
}
