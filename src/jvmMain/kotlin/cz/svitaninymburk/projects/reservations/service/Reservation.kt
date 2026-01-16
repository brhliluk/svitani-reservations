package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid


class ReservationService(
    private val eventRepository: EventInstanceRepository,
    private val reservationRepository: ReservationRepository,
    private val emailService: EmailService,
    private val qrCodeService: QrCodeService,
    private val paymentTrigger: PaymentTrigger,
): ReservationServiceInterface {
    override suspend fun createReservation(request: CreateReservationRequest, userId: String?): Either<ReservationError.CreateReservation, Reservation> = either {

        val instance = ensureNotNull(eventRepository.get(request.eventInstanceId)) { ReservationError.EventNotFound }

        ensure(!instance.isCancelled) { ReservationError.EventCancelled }
        ensure(instance.endDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyFinished }
        ensure(instance.startDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyStarted }

        val isReserved = eventRepository.attemptToReserveSpots(
            instanceId = instance.id,
            amount = request.seatCount,
        )

        ensure(isReserved) { ReservationError.CapacityExceeded }

        val reservation = reservationRepository.save(
            Reservation(
                id = Uuid.random().toString(),
                eventInstanceId = request.eventInstanceId,
                registeredUserId = userId,
                seatCount = request.seatCount,
                contactName = request.contactName,
                contactEmail = request.contactEmail,
                contactPhone = request.contactPhone,
                paymentType = request.paymentType,
                totalPrice = instance.price * request.seatCount,
                status = Reservation.Status.PENDING_PAYMENT,
                createdAt = Clock.System.now(),
                customValues = request.customValues,
            )
        )

        eventRepository.incrementOccupiedSpots(instance.id, request.seatCount)

        emailService.sendReservationConfirmation(
            reservation.contactEmail,
            reservation,
            qrCodeService.accountNumber,
            qrCodeService.generateQrPaymentImage(reservation)
        )

        paymentTrigger.notifyNewReservation()

        reservation
    }

    override suspend fun cancelReservation(reservationId: String): Either<ReservationError.CancelReservation, Boolean> = either {
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) { ReservationError.EventNotFound }

        ensure(Clock.System.now() < reservation.createdAt) { ReservationError.EventAlreadyFinished }

        val cancelledReservation = reservation.copy(status = Reservation.Status.CANCELLED)
        reservationRepository.save(cancelledReservation)

        eventRepository.decrementOccupiedSpots(reservation.eventInstanceId, reservation.seatCount)

        emailService.sendCancellationNotice(cancelledReservation.contactEmail, reservationId)
            .mapLeft { ReservationError.FailedToSendCancellationEmail(it) }
        true
    }
}

class AuthenticatedReservationService(
    private val eventRepository: EventInstanceRepository,
    private val reservationRepository: ReservationRepository,
) : AuthenticatedReservationServiceInterface {
    override suspend fun getReservations(userId: String): Either<ReservationError.GetAll, List<Reservation>> = either {
        val reservations = reservationRepository.getAll(userId)
        if (reservations.isEmpty()) return@either emptyList()

        val eventIds = reservations.map { it.eventInstanceId }.distinct()

        val events = eventRepository.getAll(eventIds).associateBy { it.id }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        reservations.filter { reservation ->
            val event = events[reservation.eventInstanceId]
            event != null && event.endDateTime > now && !event.isCancelled
        }
    }
}