package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.qr.QrCodeService
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid


class ReservationService(
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val reservationRepository: ReservationRepository,
    private val emailService: EmailService,
    private val qrCodeService: BackendQrCodeGenerator,
    private val paymentTrigger: PaymentTrigger,
): ReservationServiceInterface {
    override suspend fun reserveInstance(request: CreateInstanceReservationRequest, userId: String?): Either<ReservationError.CreateReservation, Reservation> = either {

        val instance = ensureNotNull(eventInstanceRepository.get(request.eventInstanceId)) { ReservationError.NotFound }

        ensure(!instance.isCancelled) { ReservationError.EventCancelled }
        ensure(instance.endDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyFinished }
        ensure(instance.startDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyStarted }

        val isReserved = eventInstanceRepository.attemptToReserveSpots(instanceId = instance.id, amount = request.seatCount,)

        ensure(isReserved) { ReservationError.CapacityExceeded }

        val reservation = reservationRepository.save(
            Reservation(
                id = Uuid.random().toString(),
                reference = Reference.Instance(request.eventInstanceId),
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
        finalizeReservation(reservation, instance.id)
    }

    override suspend fun reserveSeries(
        request: CreateSeriesReservationRequest,
        userId: String?
    ): Either<ReservationError.CreateReservation, Reservation> = either {

        val series = ensureNotNull(eventSeriesRepository.findById(request.eventSeriesId)) { ReservationError.NotFound }

        val isReserved = eventSeriesRepository.attemptToReserveSpots(series.id, request.seatCount)
        ensure(isReserved) { ReservationError.CapacityExceeded }

        val reservation = Reservation(
            id = Uuid.random().toString(),
            reference = Reference.Series(request.eventSeriesId),
            registeredUserId = userId,
            seatCount = request.seatCount,
            contactName = request.contactName,
            contactEmail = request.contactEmail,
            contactPhone = request.contactPhone,
            paymentType = request.paymentType,
            totalPrice = series.price * request.seatCount,
            status = Reservation.Status.PENDING_PAYMENT,
            createdAt = Clock.System.now(),
            customValues = request.customValues,
        )

        finalizeReservation(reservation, series.id)
    }

    private suspend fun Raise<ReservationError.CreateReservation>.finalizeReservation(
        reservation: Reservation,
        resourceId: String,
    ): Reservation {

        reservationRepository.save(reservation)

        when (reservation.reference) {
            is Reference.Instance -> eventInstanceRepository.incrementOccupiedSpots(resourceId, reservation.seatCount)
            is Reference.Series -> eventSeriesRepository.incrementOccupiedSpots(resourceId, reservation.seatCount)
        }

        val qrImage = qrCodeService.generateQrPng(reservation)
        emailService.sendReservationConfirmation(
            reservation.contactEmail,
            reservation,
            qrCodeService.accountNumber,
            qrImage
        )

        paymentTrigger.notifyNewReservation()

        return reservation
    }

    override suspend fun cancelReservation(reservationId: String): Either<ReservationError.CancelReservation, Boolean> = either {
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) { ReservationError.NotFound }

        ensure(Clock.System.now() < reservation.createdAt) { ReservationError.EventAlreadyFinished }

        val cancelledReservation = reservation.copy(status = Reservation.Status.CANCELLED)
        reservationRepository.save(cancelledReservation)

        when (reservation.reference) {
            is Reference.Instance -> eventInstanceRepository.decrementOccupiedSpots(reservation.reference.id, reservation.seatCount)
            is Reference.Series -> eventSeriesRepository.decrementOccupiedSpots(reservation.reference.id, reservation.seatCount)
        }

        emailService.sendCancellationNotice(cancelledReservation.contactEmail, reservationId)
            .mapLeft { ReservationError.FailedToSendCancellationEmail(it) }
        true
    }
}

class AuthenticatedReservationService(
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val reservationRepository: ReservationRepository,
) : AuthenticatedReservationServiceInterface {
    override suspend fun getReservations(userId: String): Either<ReservationError.GetAll, List<Reservation>> = either {
        val reservations = reservationRepository.getAll(userId)
        if (reservations.isEmpty()) return@either emptyList()

        val events = eventInstanceRepository.getAll(reservations.filter { it.reference is Reference.Instance }.map { it.id }).associateBy { it.id }
        val series = eventSeriesRepository.getAll(reservations.filter { it.reference is Reference.Series }.map { it.id }).associateBy { it.id }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        reservations.filter { reservation ->
            val event = events[reservation.reference.id]
            val series = series[reservation.reference.id]
            (event != null && event.endDateTime > now && !event.isCancelled
                    || series != null && series.endDate > now.date)
        }
    }
}