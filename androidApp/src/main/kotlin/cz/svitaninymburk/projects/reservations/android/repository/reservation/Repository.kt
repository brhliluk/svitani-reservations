package cz.svitaninymburk.projects.reservations.android.repository.reservation

import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthLocalDataSource
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
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
    private val dataSource: AuthLocalDataSource,
) : ReservationsRepository {

    override suspend fun getMyReservations(): Either<RepositoryError, List<MyReservationListItem>> =
        httpClient.authGet("/api/v1/reservations/mine", dataSource.getAccessToken())

    override suspend fun getPaymentInfo(id: Uuid): Either<RepositoryError, MobilePaymentInfo> =
        httpClient.authGet("/api/v1/reservations/$id/payment", dataSource.getAccessToken())

    override suspend fun cancelReservation(id: Uuid): Either<RepositoryError, Unit> =
        httpClient.authPostNoBody("/api/v1/reservations/$id/cancel", dataSource.getAccessToken())

    override suspend fun createInstanceReservation(
        request: CreateInstanceReservationRequest,
    ): Either<RepositoryError, Reservation> =
        httpClient.authPost("/api/v1/reservations/instance", dataSource.getAccessToken(), request)

    override suspend fun createSeriesReservation(
        request: CreateSeriesReservationRequest,
    ): Either<RepositoryError, Reservation> =
        httpClient.authPost("/api/v1/reservations/series", dataSource.getAccessToken(), request)
}
