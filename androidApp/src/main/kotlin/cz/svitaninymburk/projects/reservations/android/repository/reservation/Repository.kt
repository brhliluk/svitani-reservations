package cz.svitaninymburk.projects.reservations.android.repository.reservation

import android.content.SharedPreferences
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.accessToken
import cz.svitaninymburk.projects.reservations.android.repository.authGet
import cz.svitaninymburk.projects.reservations.android.repository.authPost
import cz.svitaninymburk.projects.reservations.android.repository.authPostNoBody
import cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import io.ktor.client.HttpClient
import kotlin.uuid.Uuid

class ReservationsRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : ReservationsRepository {

    override suspend fun getMyReservations(): Either<RepositoryError, List<MyReservationListItem>> =
        httpClient.authGet("/api/v1/reservations/mine", prefs.accessToken)

    override suspend fun getPaymentInfo(id: Uuid): Either<RepositoryError, MobilePaymentInfo> =
        httpClient.authGet("/api/v1/reservations/$id/payment", prefs.accessToken)

    override suspend fun cancelReservation(id: Uuid): Either<RepositoryError, Unit> =
        httpClient.authPostNoBody("/api/v1/reservations/$id/cancel", prefs.accessToken)

    override suspend fun createInstanceReservation(
        request: CreateInstanceReservationRequest,
    ): Either<RepositoryError, Reservation> =
        httpClient.authPost("/api/v1/reservations/instance", prefs.accessToken, request)

    override suspend fun createSeriesReservation(
        request: CreateSeriesReservationRequest,
    ): Either<RepositoryError, Reservation> =
        httpClient.authPost("/api/v1/reservations/series", prefs.accessToken, request)
}
