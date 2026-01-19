package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import dev.kilua.rpc.annotations.RpcService


@RpcService
interface ReservationServiceInterface {
    suspend fun reserveInstance(request: CreateInstanceReservationRequest, userId: String?): Either<ReservationError.CreateReservation, Reservation>
    suspend fun reserveSeries(request: CreateSeriesReservationRequest, userId: String?): Either<ReservationError.CreateReservation, Reservation>
    suspend fun cancelReservation(reservationId: String): Either<ReservationError.CancelReservation, Boolean>
}

@RpcService
interface AuthenticatedReservationServiceInterface {
    suspend fun getReservations(userId: String): Either<ReservationError.GetAll, List<Reservation>>
}
