package cz.svitaninymburk.projects.reservations.api

import cz.svitaninymburk.projects.reservations.error.AppError
import cz.svitaninymburk.projects.reservations.error.AuthError
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.error.ReservationError
import io.ktor.http.HttpStatusCode

fun AppError.code(): String = this::class.simpleName ?: "Unknown"

fun AppError.httpStatus(): HttpStatusCode = when (this) {
    is AuthError.InvalidCredentials,
    is AuthError.InvalidToken,
    is AuthError.TokenExpired,
    is AuthError.NoJwtPrincipal,
    is AuthError.NoIdInPrincipal -> HttpStatusCode.Unauthorized

    is AuthError.UserNotFound,
    is ReservationError.ReservationNotFound,
    is ReservationError.EventInstanceNotFound,
    is ReservationError.EventSeriesNotFound,
    is EventError.EventInstanceNotFound,
    is EventError.EventDefinitionNotFound,
    is EventError.EventSeriesNotFound -> HttpStatusCode.NotFound

    is ReservationError.CapacityExceeded,
    is AuthError.UserAlreadyExists -> HttpStatusCode.Conflict

    else -> HttpStatusCode.BadRequest
}
