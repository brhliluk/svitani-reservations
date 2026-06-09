package cz.svitaninymburk.projects.reservations.android.repository.reservation

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import kotlin.uuid.Uuid

interface ReservationsRepository {
    suspend fun getMyReservations(): Either<RepositoryError, List<MyReservationListItem>>
    suspend fun getPaymentInfo(id: Uuid): Either<RepositoryError, MobilePaymentInfo>
    suspend fun cancelReservation(id: Uuid): Either<RepositoryError, Unit>
}
