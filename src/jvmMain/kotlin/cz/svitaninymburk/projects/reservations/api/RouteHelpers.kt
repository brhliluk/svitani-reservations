package cz.svitaninymburk.projects.reservations.api

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.AppError
import cz.svitaninymburk.projects.reservations.error.AuthError
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.CsErrorStrings
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.user.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import kotlin.uuid.Uuid

fun AppError.localized(): String {
    val e = this
    return when (e) {
        is AuthError -> e.localizedMessage(CsErrorStrings)
        is ReservationError -> e.localizedMessage(CsErrorStrings)
        is EventError -> e.localizedMessage(CsErrorStrings)
        else -> code()
    }
}

suspend inline fun <reified R : Any, L : AppError> ApplicationCall.respondEither(
    result: Either<L, R>,
) = result
        .onLeft { error -> respond(error.httpStatus(), ApiError(code = error.code(), message = error.localized())) }
        .onRight { value -> respond(HttpStatusCode.OK, value) }

suspend fun ApplicationCall.requireAdmin(authService: AuthServiceInterface): Boolean {
    val user = authService.getCurrentUser().getOrNull()
    return if (user?.role == User.Role.ADMIN) true
    else {
        respond(HttpStatusCode.Forbidden, ApiError("Forbidden", "Přístup odepřen — pouze pro administrátory"))
        false
    }
}

fun ApplicationCall.jwtUserId(): Uuid? =
    principal<JWTPrincipal>()?.payload?.getClaim("id")?.asString()
        ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
