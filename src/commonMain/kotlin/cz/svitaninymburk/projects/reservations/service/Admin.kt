package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.reservation.AdminDashboardData
import dev.kilua.rpc.annotations.RpcService

@RpcService
interface AdminServiceInterface {
    suspend fun getDashboardSummary(): Either<AdminError.GetSummary, AdminDashboardData>
}