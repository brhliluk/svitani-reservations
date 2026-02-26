package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.reservation.AdminDashboardData
import dev.kilua.rpc.annotations.RpcService
import kotlin.uuid.Uuid

@RpcService
interface AdminServiceInterface {
    suspend fun getDashboardSummary(): Either<AdminError.GetSummary, AdminDashboardData>
    suspend fun markReservationAsPaid(reservationId: Uuid): Either<AdminError.MarkReservationPaid, Unit>
}