package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationDetail
import cz.svitaninymburk.projects.reservations.reservation.ReservationRequestData
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
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
    override suspend fun get(id: String): Either<ReservationError.Get, Reservation> = either {
        reservationRepository.findById(id) ?: raise(ReservationError.ReservationNotFound)
    }

    override suspend fun getDetail(id: String): Either<ReservationError.GetDetail, ReservationDetail> = either {
        val reservation = get(id).getOrElse { raise(ReservationError.ReservationNotFound) }

        val target: ReservationTarget = when (val ref = reservation.reference) {
            is Reference.Instance -> {
                val event = eventInstanceRepository.get(ref.id) ?: raise(ReservationError.EventInstanceNotFound)
                ReservationTarget.Instance(event)
            }
            is Reference.Series -> {
                val series = eventSeriesRepository.get(ref.id) ?: raise(ReservationError.EventSeriesNotFound)
                ReservationTarget.Series(series)
            }
        }

        ReservationDetail(reservation, target)
    }


    override suspend fun reserveInstance(request: CreateInstanceReservationRequest, userId: String?): Either<ReservationError.CreateReservation, Reservation> = either {

        val instance = ensureNotNull(eventInstanceRepository.get(request.eventInstanceId)) { ReservationError.ReservationNotFound }

        ensure(!instance.isCancelled) { ReservationError.EventCancelled }
        ensure(instance.endDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyFinished }
        ensure(instance.startDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyStarted }

        val isReserved = eventInstanceRepository.attemptToReserveSpots(instanceId = instance.id, amount = request.seatCount,)

        ensure(isReserved) { ReservationError.CapacityExceeded }

        createReservationFlow(
            reference = Reference.Instance(instance.id),
            userId = userId,
            requestData = request,
            pricePerSeat = instance.price
        )
    }

    override suspend fun reserveSeries(
        request: CreateSeriesReservationRequest,
        userId: String?
    ): Either<ReservationError.CreateReservation, Reservation> = either {

        val series = ensureNotNull(eventSeriesRepository.get(request.eventSeriesId)) { ReservationError.ReservationNotFound }

        val isReserved = eventSeriesRepository.attemptToReserveSpots(series.id, request.seatCount)
        ensure(isReserved) { ReservationError.CapacityExceeded }

        createReservationFlow(
            reference = Reference.Instance(series.id),
            userId = userId,
            requestData = request,
            pricePerSeat = series.price
        )
    }

    private suspend fun Raise<ReservationError.CreateReservation>.createReservationFlow(
        reference: Reference,
        userId: String?,
        requestData: ReservationRequestData,
        pricePerSeat: Double,
    ): Reservation {
        val variableSymbol = generateUniqueVariableSymbol()

        val reservation = Reservation(
            id = Uuid.random().toString(),
            reference = reference,
            registeredUserId = userId,
            seatCount = requestData.seatCount,
            contactName = requestData.contactName,
            contactEmail = requestData.contactEmail,
            contactPhone = requestData.contactPhone,
            paymentType = requestData.paymentType,
            customValues = requestData.customValues,
            totalPrice = pricePerSeat * requestData.seatCount,
            status = Reservation.Status.PENDING_PAYMENT,
            createdAt = Clock.System.now(),
            variableSymbol = variableSymbol,
        )

        reservationRepository.save(reservation)

        when (reference) {
            is Reference.Instance -> eventInstanceRepository.incrementOccupiedSpots(reference.id, reservation.seatCount)
            is Reference.Series -> eventSeriesRepository.incrementOccupiedSpots(reference.id, reservation.seatCount)
        }

        val qrImage = qrCodeService.generateQrPng(reservation)

        emailService.sendReservationConfirmation(
            reservation.contactEmail,
            reservation,
            qrCodeService.accountNumber,
            qrImage
        ).onLeft {
            println("⚠️ Failed to send confirmation email: $it")
        }

        paymentTrigger.notifyNewReservation()

        return reservation
    }

    override suspend fun cancelReservation(reservationId: String): Either<ReservationError.CancelReservation, Boolean> = either {
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) { ReservationError.ReservationNotFound }

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

    private suspend fun Raise<ReservationError.CreateReservation>.generateUniqueVariableSymbol(): String {
        var attempts = 0
        var vs: String

        do {
            if (attempts > 10) { raise(ReservationError.SystemError("Unable to generate unique Variable Symbol")) }
            vs = generateCandidateVS()
            val exists = reservationRepository.existsByVariableSymbol(vs)
            attempts++

        } while (exists)

        return vs
    }

    private fun generateCandidateVS(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        // 1. ROK (2 znaky): "26"
        val year = now.year.toString().takeLast(2)
        // 2. DEN V ROCE (3 znaky): "030" (30. leden)
        val dayOfYear = now.dayOfYear.toString().padStart(3, '0')

        // 3. NÁHODA (5 znaků): "12345"
        // Celkem 2 + 3 + 5 = 10 znaků (Maximum pro banky)
        val random = (0..99999).random().toString().padStart(5, '0')

        return "$year$dayOfYear$random"
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