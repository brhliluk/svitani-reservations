package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.reservation.AdminDashboardData
import cz.svitaninymburk.projects.reservations.reservation.AdminPendingReservation
import cz.svitaninymburk.projects.reservations.reservation.AdminUpcomingEvent
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AdminDashboardService(
    private val eventSeriesRepository: EventSeriesRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val reservationRepository: ReservationRepository,
): AdminServiceInterface {

    override suspend fun getDashboardSummary(): Either<AdminError.GetSummary, AdminDashboardData> = either {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayStart = now.date.atTime(0, 0)
        val todayEnd = now.date.atTime(23, 59, 59)
        val endOfWeek = now.date.plus(DatePeriod(days = 7)).atTime(23, 59, 59)

        val allReservations = reservationRepository.findAll()
        val pendingReservations = allReservations.filter { it.status == Reservation.Status.PENDING_PAYMENT }

        val pendingPaymentsTotal = pendingReservations.sumOf { it.totalPrice }
        val pendingPaymentsCount = pendingReservations.size

        val todaysInstances = eventInstanceRepository.findByDateRange(todayStart, todayEnd)
        val todaysInstanceIds = todaysInstances.map { it.id }.toSet()

        val todayParticipantsCount = allReservations
            .filter { it.status != Reservation.Status.CANCELLED }
            .filter { it.reference is Reference.Instance && it.reference.id in todaysInstanceIds }
            .sumOf { it.seatCount }

        val instancesThisWeek = eventInstanceRepository.findByDateRange(now, endOfWeek).filter { !it.isCancelled }
        val freeSpotsThisWeek = instancesThisWeek.sumOf { maxOf(0, it.capacity - it.occupiedSpots) }

        val upcomingEventsData = eventInstanceRepository.findByDateRange(now, now.date.plus(DatePeriod(days = 30)).atTime(23,59))
            .filter { !it.isCancelled && it.startDateTime > now }
            .sortedBy { it.startDateTime }
            .take(5)
            .map { instance ->
                AdminUpcomingEvent(
                    id = instance.id,
                    title = instance.title,
                    startDateTime = instance.startDateTime,
                    occupiedSpots = instance.occupiedSpots,
                    capacity = instance.capacity
                )
            }

        val pendingReservationsData = pendingReservations
            .sortedByDescending { it.createdAt }
            .take(5)
            .map { res ->
                val eventName = when (val ref = res.reference) {
                    is Reference.Instance -> eventInstanceRepository.get(ref.id)?.title ?: "Neznámá událost"
                    is Reference.Series -> eventSeriesRepository.get(ref.id)?.title ?: "Neznámý kurz"
                }

                AdminPendingReservation(
                    id = res.id,
                    contactName = res.contactName,
                    eventName = eventName,
                    totalPrice = res.totalPrice,
                    variableSymbol = res.variableSymbol,
                )
            }

        AdminDashboardData(
            todayParticipantsCount = todayParticipantsCount,
            pendingPaymentsTotal = pendingPaymentsTotal,
            pendingPaymentsCount = pendingPaymentsCount,
            freeSpotsThisWeek = freeSpotsThisWeek,
            upcomingEvents = upcomingEventsData,
            pendingReservations = pendingReservationsData,
        )
    }

    override suspend fun markReservationAsPaid(reservationId: Uuid): Either<AdminError.MarkReservationPaid, Unit> = either {
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) { AdminError.ReservationNotFound(reservationId) }

        ensure(reservation.status == Reservation.Status.PENDING_PAYMENT) { AdminError.WrongReservationState(reservation.status) }

        // Změníme status v DB
        val success = reservationRepository.updateStatus(reservationId, Reservation.Status.CONFIRMED)

        if (!success) {
            raise(AdminError.FailedToMarkReservationPaid("Chyba při aktualizaci stavu v databázi."))
        }

        // 💡 TIP DO BUDOUCNA: Tady můžeš zavolat emailService a poslat mamince "Platba přijata, těšíme se na vás!"
    }
}