package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.reservation.CancellationResult
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationDetail
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonsView
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import dev.kilua.rpc.annotations.RpcService
import kotlin.uuid.Uuid


@RpcService
interface ReservationServiceInterface {
    suspend fun get(id: Uuid): Either<ReservationError.Get, Reservation>
    suspend fun getDetail(id: Uuid): Either<ReservationError.GetDetail, ReservationDetail>

    /**
     * Termíny kurzu pro odhlašovací UI. Chodí i bez přihlášení — rezervaci bez účtu
     * chrání jen znalost UUID, registrovanou její majitel.
     */
    suspend fun getSeriesLessons(reservationId: Uuid): Either<ReservationError.GetDetail, SeriesLessonsView>
    suspend fun reserveInstance(request: CreateInstanceReservationRequest, userId: Uuid?): Either<ReservationError.CreateReservation, Reservation>
    suspend fun reserveSeries(request: CreateSeriesReservationRequest, userId: Uuid?): Either<ReservationError.CreateReservation, Reservation>
    suspend fun joinWaitlistInstance(request: CreateInstanceReservationRequest, userId: Uuid?): Either<ReservationError.CreateReservation, Reservation>
    suspend fun joinWaitlistSeries(request: CreateSeriesReservationRequest, userId: Uuid?): Either<ReservationError.CreateReservation, Reservation>
    suspend fun cancelReservation(
        reservationId: Uuid,
        instanceId: Uuid? = null,
        walletCode: String? = null,
        force: Boolean = false,
    ): Either<ReservationError.CancelReservation, CancellationResult>
    suspend fun getWalletInfo(code: String, email: String): Either<ReservationError.GetWalletInfo, WalletInfo>
}

@RpcService
interface AuthenticatedReservationServiceInterface {
    suspend fun getReservations(userId: Uuid): Either<ReservationError.GetAll, List<MyReservationListItem>>

    /**
     * Připíše rezervaci bez účtu volajícímu, když sedí kontaktní e-mail.
     * Identita se bere z JWT, ne z parametru — přivlastnit jde jen sobě.
     */
    suspend fun claimReservation(reservationId: Uuid): Either<ReservationError.ClaimReservation, Unit>
}
