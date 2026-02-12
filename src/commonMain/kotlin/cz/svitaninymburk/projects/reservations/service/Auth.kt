package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.auth.RegisterRequest
import cz.svitaninymburk.projects.reservations.error.AuthError
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.rpc.annotations.RpcService

@RpcService
interface AuthServiceInterface {
    suspend fun loginWithGoogle(token: String): Either<AuthError.LoginWithGoogle, AuthResponse>
    suspend fun register(request: RegisterRequest): Either<AuthError.Register, AuthResponse>
    suspend fun login(request: LoginRequest): Either<AuthError.LoginWithEmail, AuthResponse>

    suspend fun getCurrentUser(): Either<AuthError.GetCurrentUser, User>
}

@RpcService
interface RefreshTokenServiceInterface {
    suspend fun refreshToken(token: String): Either<AuthError.RefreshToken, String>
}
