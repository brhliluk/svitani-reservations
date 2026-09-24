package cz.svitaninymburk.projects.reservations.api

import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.error.AuthError
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.i18n.CsErrorStrings
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

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

    // Dřív AdminError propadl do `else -> code()` a admin místo hlášky viděl název třídy.
    @Test
    fun adminErrorIsLocalizedNotClassName() {
        val error = AdminError.SeriesNotFoundForEdit(Uuid.random())
        assertEquals(CsErrorStrings.errorAdminCourseNotFound, error.localized())
    }

    @Test
    fun adminErrorWithMessageKeepsMessage() {
        assertEquals("disk je plný", AdminError.FailedToCreateSeries("disk je plný").localized())
    }
}
