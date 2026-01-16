package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.reservation.CreateReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import dev.kilua.rpc.annotations.RpcService


@RpcService
interface ReservationServiceInterface {
    suspend fun createReservation(request: CreateReservationRequest, userId: String?): Either<ReservationError.CreateReservation, Reservation>
    suspend fun cancelReservation(reservationId: String): Either<ReservationError.CancelReservation, Boolean>
}

@RpcService
interface AuthenticatedReservationServiceInterface {
    suspend fun getReservations(userId: String): Either<ReservationError.GetAll, List<Reservation>>
}
