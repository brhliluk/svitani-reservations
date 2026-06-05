package cz.svitaninymburk.projects.reservations.api

import cz.svitaninymburk.projects.reservations.error.AuthError
import cz.svitaninymburk.projects.reservations.error.ReservationError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorHttpTest {
    @Test
    fun mapsNotFoundTo404() {
        assertEquals(HttpStatusCode.NotFound, ReservationError.ReservationNotFound.httpStatus())
    }

    @Test
    fun mapsInvalidCredentialsTo401() {
        assertEquals(HttpStatusCode.Unauthorized, AuthError.InvalidCredentials.httpStatus())
    }

    @Test
    fun mapsCapacityExceededTo409() {
        assertEquals(HttpStatusCode.Conflict, ReservationError.CapacityExceeded.httpStatus())
    }

    @Test
    fun unknownErrorDefaultsToBadRequest() {
        assertEquals(HttpStatusCode.BadRequest, ReservationError.AlreadyOptedOut.httpStatus())
    }
}
