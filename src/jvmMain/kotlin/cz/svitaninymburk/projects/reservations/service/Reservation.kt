package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.calculateTotalPrice
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.CancellationResult
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationDetail
import cz.svitaninymburk.projects.reservations.reservation.ReservationRequestData
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonItem
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import cz.svitaninymburk.projects.reservations.reservation.SeriesReservationDetail
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.util.currentCall
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.reflect.jvm.jvmName
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid


open class ReservationService(
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val eventDefinitionRepository: EventDefinitionRepository,
    private val reservationRepository: ReservationRepository,
    private val emailService: EmailService,
    private val lectorEmailService: LectorEmailService,
    private val qrCodeService: QrCodeGeneratorService,
    private val paymentTrigger: PaymentTrigger,
    private val appBaseUrl: String,
    private val seriesLessonOptOutRepository: SeriesLessonOptOutRepository,
    private val walletService: WalletService,
    private val walletEmailService: WalletEmailService,
    private val appSettingsProvider: AppSettingsProvider,
) : ReservationServiceInterface {

    private val logger = KtorSimpleLogger(this::class.jvmName)

    /** Returns the authenticated caller's UUID from the JWT principal, or null if not authenticated. */
    internal open suspend fun currentCallerUserId(): Uuid? {
        val idString = currentCall()
            ?.principal<JWTPrincipal>()
            ?.payload?.getClaim("id")?.asString()
            ?: return null
        return runCatching { Uuid.parse(idString) }.getOrNull()
    }
    override suspend fun get(id: Uuid): Either<ReservationError.Get, Reservation> = either {
        reservationRepository.findById(id) ?: raise(ReservationError.ReservationNotFound)
    }

    override suspend fun getDetail(id: Uuid): Either<ReservationError.GetDetail, ReservationDetail> = either {
        val reservation = get(id).getOrElse { raise(ReservationError.ReservationNotFound) }

        val target: ReservationTarget? = when (val ref = reservation.reference) {
            is Reference.Instance -> eventInstanceRepository.get(ref.id)?.let { ReservationTarget.Instance(it) }
            is Reference.Series -> eventSeriesRepository.get(ref.id)?.let { ReservationTarget.Series(it) }
        }

        ReservationDetail(reservation, target, qrCodeService.accountNumber)
    }


    override suspend fun reserveInstance(request: CreateInstanceReservationRequest, userId: Uuid?): Either<ReservationError.CreateReservation, Reservation> = either {

        val instance = ensureNotNull(eventInstanceRepository.get(request.eventInstanceId)) { ReservationError.ReservationNotFound }

        ensure(!instance.isCancelled) { ReservationError.EventCancelled }
        ensure(instance.endDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyFinished }
        ensure(instance.startDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyStarted }
        ensure(!instance.isDeadlinePassed) { ReservationError.ReservationDeadlinePassed }

        val isReserved = eventInstanceRepository.attemptToReserveSpots(instanceId = instance.id, amount = request.seatCount,)

        ensure(isReserved) { ReservationError.CapacityExceeded }

        val target = ReservationTarget.Instance(instance)

        createReservationFlow(
            reference = Reference.Instance(instance.id),
            userId = userId,
            requestData = request,
            pricePerSeat = instance.price,
            target = target,
            walletCode = request.walletCode,
        )
    }

    override suspend fun reserveSeries(
        request: CreateSeriesReservationRequest,
        userId: Uuid?
    ): Either<ReservationError.CreateReservation, Reservation> = either {

        val series = ensureNotNull(eventSeriesRepository.get(request.eventSeriesId)) { ReservationError.ReservationNotFound }

        ensure(!series.isDeadlinePassed) { ReservationError.ReservationDeadlinePassed }

        val isReserved = eventSeriesRepository.attemptToReserveSpots(series.id, request.seatCount)
        ensure(isReserved) { ReservationError.CapacityExceeded }

        val seriesTarget = ReservationTarget.Series(series)

        createReservationFlow(
            reference = Reference.Series(series.id),
            userId = userId,
            requestData = request,
            pricePerSeat = series.price,
            target = seriesTarget,
            walletCode = request.walletCode,
        )
    }

    private suspend fun Raise<ReservationError.CreateReservation>.createReservationFlow(
        reference: Reference,
        userId: Uuid?,
        requestData: ReservationRequestData,
        pricePerSeat: Double,
        target: ReservationTarget,
        walletCode: String? = null,
    ): Reservation {
        val variableSymbol = generateUniqueVariableSymbol()

        val reservation = Reservation(
            id = Uuid.random(),
            reference = reference,
            registeredUserId = userId,
            seatCount = requestData.seatCount,
            contactName = requestData.contactName,
            contactEmail = requestData.contactEmail,
            contactPhone = requestData.contactPhone,
            paymentType = requestData.paymentType,
            customValues = requestData.customValues,
            totalPrice = calculateTotalPrice(
                basePrice = pricePerSeat,
                seatCount = requestData.seatCount,
                customFields = target.customFields,
                customValues = requestData.customValues,
            ),
            status = Reservation.Status.PENDING_PAYMENT,
            createdAt = Clock.System.now(),
            variableSymbol = variableSymbol,
            locale = requestData.locale,
        )

        var savedReservation = reservationRepository.save(reservation)

        // Apply wallet debit if code provided
        if (walletCode != null) {
            val walletResult = walletService.validateForReservation(walletCode)
            if (walletResult.isRight()) {
                val wallet = walletResult.getOrNull()!!
                val deductAmount = minOf(wallet.balance, reservation.totalPrice)
                if (deductAmount > 0.0) {
                    walletService.debit(wallet.id, deductAmount, WalletTransactionReason.RESERVATION_DEBIT, reservation.id)
                    val fullyPaid = deductAmount == reservation.totalPrice
                    savedReservation = reservationRepository.save(
                        reservation.copy(
                            walletId = wallet.id,
                            walletDeductedAmount = deductAmount,
                            paidAmount = deductAmount,
                            status = if (fullyPaid) Reservation.Status.CONFIRMED else reservation.status,
                            paymentType = if (fullyPaid) PaymentInfo.Type.FREE else reservation.paymentType,
                        )
                    )
                    walletEmailService.sendWalletApplied(
                        toEmail = reservation.contactEmail,
                        walletCode = wallet.code,
                        deductedAmount = deductAmount,
                        remainingBalance = wallet.balance - deductAmount,
                        locale = reservation.locale,
                    ).onLeft { logger.error("Failed to send wallet applied email to ${reservation.contactEmail}: $it") }
                }
            }
            // If wallet validation fails (not found / empty), silently ignore and proceed without wallet
        }

        val qrImage: ByteArray? = if (savedReservation.paymentType == PaymentInfo.Type.BANK_TRANSFER) {
            qrCodeService.generateQrPng(savedReservation)
        } else null

        val icalBytes = when (target) {
            is ReservationTarget.Instance -> ICalGenerator.forInstance(target.event, savedReservation.id, appBaseUrl)
            is ReservationTarget.Series -> ICalGenerator.forSeries(target.series, savedReservation.id, appBaseUrl)
        }.toByteArray(Charsets.UTF_8)

        emailService.sendReservationConfirmation(
            toEmail = savedReservation.contactEmail,
            reservation = savedReservation,
            target = target,
            bankAccount = qrCodeService.accountNumber,
            qrCodeImage = qrImage,
            icalBytes = icalBytes,
        ).onLeft { logger.error("Failed to send confirmation email for reservation ${savedReservation.id}: $it") }

        val ownerEmails = resolveOwnerEmails(target)
        if (ownerEmails.isNotEmpty()) {
            val newOccupiedSpots = when (target) {
                is ReservationTarget.Instance -> target.event.occupiedSpots + savedReservation.seatCount
                is ReservationTarget.Series -> target.series.occupiedSpots + savedReservation.seatCount
            }
            val capacity = when (target) {
                is ReservationTarget.Instance -> target.event.capacity
                is ReservationTarget.Series -> target.series.capacity
            }
            ownerEmails.forEach { email ->
                lectorEmailService.sendLectorReservationNotification(
                    lectorEmail = email,
                    contactName = savedReservation.contactName,
                    contactEmail = savedReservation.contactEmail,
                    contactPhone = savedReservation.contactPhone,
                    seatCount = savedReservation.seatCount,
                    eventTitle = target.title,
                    occupiedSpots = newOccupiedSpots,
                    capacity = capacity,
                    locale = savedReservation.locale,
                ).onLeft { logger.error("Failed to send owner reservation email to $email: $it") }
            }
        }

        paymentTrigger.notifyNewReservation()

        return savedReservation
    }

    override suspend fun cancelReservation(
        reservationId: Uuid,
        instanceId: Uuid?,
        walletCode: String?,
        force: Boolean,
    ): Either<ReservationError.CancelReservation, CancellationResult> = either {
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) { ReservationError.ReservationNotFound }

        if (instanceId != null) {
            // Lesson opt-out path — verify that the caller owns this reservation
            val callerUuid = currentCallerUserId()
            ensure(callerUuid != null && reservation.registeredUserId == callerUuid) {
                ReservationError.ReservationNotFound  // Don't reveal existence to non-owner
            }

            ensure(reservation.reference is Reference.Series) { ReservationError.NotASeriesReservation }
            val seriesId = reservation.reference.id

            val instance = ensureNotNull(eventInstanceRepository.get(instanceId)) {
                ReservationError.InstanceNotInSeries
            }
            ensure(instance.seriesId == seriesId) { ReservationError.InstanceNotInSeries }
            ensure(!instance.isCancelled) { ReservationError.EventAlreadyFinished }
            ensure(
                Clock.System.now() < instance.startDateTime.toInstant(TimeZone.currentSystemDefault())
            ) { ReservationError.EventAlreadyStarted }
            ensure(
                seriesLessonOptOutRepository.findByReservationAndInstance(reservationId, instanceId) == null
            ) { ReservationError.AlreadyOptedOut }

            val now = Clock.System.now()
            val timezone = TimeZone.of("Europe/Prague")
            val deadlineInstant = instance.startDateTime.date
                .minus(1, DateTimeUnit.DAY)
                .atTime(18, 0)
                .toInstant(timezone)
            val isLate = now > deadlineInstant

            seriesLessonOptOutRepository.save(
                SeriesLessonOptOut(
                    id = Uuid.random(),
                    reservationId = reservationId,
                    instanceId = instanceId,
                    optedOutAt = now,
                    isLateCancellation = isLate,
                )
            )
            eventInstanceRepository.decrementOccupiedSpots(instanceId, reservation.seatCount)

            emailService.sendLessonOptOutNotice(
                toEmail = reservation.contactEmail,
                eventTitle = instance.title,
                lessonDate = instance.startDateTime.date,
                isLateCancellation = isLate,
                locale = reservation.locale,
            ).onLeft { logger.error("Failed to send opt-out email to ${reservation.contactEmail}: $it") }

            val ownerEmails = instance.ownerEmails
            ownerEmails.forEach { ownerEmail ->
                lectorEmailService.sendLectorLessonOptOutNotification(
                    lectorEmail = ownerEmail,
                    contactName = reservation.contactName,
                    eventTitle = instance.title,
                    lessonDate = instance.startDateTime.date,
                    isLateCancellation = isLate,
                    locale = reservation.locale,
                ).onLeft { logger.error("Failed to send owner opt-out email to $ownerEmail: $it") }
            }

            // Wallet credit for lesson opt-out
            val optOut = seriesLessonOptOutRepository.findByReservationAndInstance(reservationId, instanceId)
            val series = eventSeriesRepository.get(reservation.reference.id)
            val refundAmount: Double = when {
                reservation.paidAmount <= 0.0 -> 0.0  // nothing was paid, no refund
                optOut?.isLateCancellation == true -> 0.0  // late cancellation, no refund
                else -> series?.lessonRefundAmount ?: 0.0
            }
            if (refundAmount > 0.0) {
                // registeredUserId is always non-null here: the opt-out path enforces
                // callerUuid != null && reservation.registeredUserId == callerUuid above.
                val wallet: Wallet = walletService.findOrCreateForRegisteredUser(
                    reservation.registeredUserId!!, reservation.contactEmail
                )
                val updatedWallet = walletService.credit(
                    wallet.id, refundAmount, WalletTransactionReason.LESSON_OPT_OUT_REFUND, reservationId
                )
                val settings = appSettingsProvider.current
                walletEmailService.sendWalletCredited(
                    toEmail = reservation.contactEmail,
                    walletCode = updatedWallet.code,
                    creditedAmount = refundAmount,
                    newBalance = updatedWallet.balance,
                    resetMonth = settings.seasonResetMonth,
                    resetDay = settings.seasonResetDay,
                    locale = reservation.locale,
                ).onLeft { println("⚠️ Failed to send wallet credited email to ${reservation.contactEmail}: $it") }
                CancellationResult(walletCode = updatedWallet.code, walletCreditAmount = refundAmount)
            } else {
                CancellationResult()
            }
        } else {
            // Whole-reservation cancellation path
            val target: ReservationTarget? = when (reservation.reference) {
                is Reference.Instance -> eventInstanceRepository.get(reservation.reference.id)?.let { ReservationTarget.Instance(it) }
                is Reference.Series -> eventSeriesRepository.get(reservation.reference.id)?.let { ReservationTarget.Series(it) }
            }

            if (target != null) {
                ensure(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) < target.startDateTime) { ReservationError.EventAlreadyFinished }
            }

            val cancelledReservation = reservation.copy(status = Reservation.Status.CANCELLED)
            reservationRepository.save(cancelledReservation)

            if (target == null) {
                // Referenced event was deleted — still cancel the reservation, skip side effects
                CancellationResult()
            } else {
                val updatedSpots = when (reservation.reference) {
                    is Reference.Instance -> eventInstanceRepository.decrementOccupiedSpots(reservation.reference.id, reservation.seatCount)
                    is Reference.Series -> eventSeriesRepository.decrementOccupiedSpots(reservation.reference.id, reservation.seatCount)
                } ?: when (target) {
                    is ReservationTarget.Instance -> (target.event.occupiedSpots - reservation.seatCount).coerceAtLeast(0)
                    is ReservationTarget.Series -> (target.series.occupiedSpots - reservation.seatCount).coerceAtLeast(0)
                }

                emailService.sendCancellationNotice(cancelledReservation.contactEmail, target.title, cancelledReservation.id, cancelledReservation.locale)
                    .mapLeft { ReservationError.FailedToSendCancellationEmail(it) }.bind()

                val ownerEmails = resolveOwnerEmails(target)
                if (ownerEmails.isNotEmpty()) {
                    val capacity = when (target) {
                        is ReservationTarget.Instance -> target.event.capacity
                        is ReservationTarget.Series -> target.series.capacity
                    }
                    ownerEmails.forEach { email ->
                        lectorEmailService.sendLectorCancellationNotification(
                            lectorEmail = email,
                            contactName = cancelledReservation.contactName,
                            eventTitle = target.title,
                            seatCount = cancelledReservation.seatCount,
                            occupiedSpots = updatedSpots,
                            capacity = capacity,
                            locale = cancelledReservation.locale,
                        ).onLeft { logger.error("Failed to send owner cancellation email to $email: $it") }
                    }
                }

                // Wallet credit for whole-reservation cancellation — only within the deadline (18:00 day before)
                val paidAmount = reservation.paidAmount
                val timezone = TimeZone.of("Europe/Prague")
                val cancellationDeadline = target.startDateTime.date
                    .minus(1, DateTimeUnit.DAY)
                    .atTime(18, 0)
                    .toInstant(timezone)
                val withinCancellationWindow = Clock.System.now() < cancellationDeadline
                if (paidAmount > 0.0 && withinCancellationWindow) {
                    val reservationRegisteredUserId = reservation.registeredUserId
                    val wallet: Wallet = if (reservationRegisteredUserId != null) {
                        walletService.findOrCreateForRegisteredUser(reservationRegisteredUserId, reservation.contactEmail)
                    } else {
                        val resolved = walletService.resolveAnonymousWallet(walletCode, reservation.contactEmail, force)
                        when (val r = resolved) {
                            is Either.Left -> raise(r.value)
                            is Either.Right -> r.value
                        }
                    }

                    var updatedWallet = wallet
                    if (reservation.walletDeductedAmount > 0.0) {
                        // Reverse the wallet debit first
                        updatedWallet = walletService.credit(
                            wallet.id, reservation.walletDeductedAmount,
                            WalletTransactionReason.RESERVATION_DEBIT_REVERSAL, reservationId
                        )
                        // Refund the cash portion
                        val cashPaid = paidAmount - reservation.walletDeductedAmount
                        if (cashPaid > 0.0) {
                            updatedWallet = walletService.credit(
                                wallet.id, cashPaid, WalletTransactionReason.CANCELLATION_REFUND, reservationId
                            )
                        }
                    } else {
                        updatedWallet = walletService.credit(
                            wallet.id, paidAmount, WalletTransactionReason.CANCELLATION_REFUND, reservationId
                        )
                    }
                    val settings = appSettingsProvider.current
                    walletEmailService.sendWalletCredited(
                        toEmail = reservation.contactEmail,
                        walletCode = updatedWallet.code,
                        creditedAmount = paidAmount,
                        newBalance = updatedWallet.balance,
                        resetMonth = settings.seasonResetMonth,
                        resetDay = settings.seasonResetDay,
                        locale = reservation.locale,
                    ).onLeft { logger.error("Failed to send wallet credited email to ${reservation.contactEmail}: $it") }
                    CancellationResult(walletCode = updatedWallet.code, walletCreditAmount = paidAmount)
                } else {
                    CancellationResult()
                }
            }
        }
    }

    override suspend fun getWalletInfo(code: String, email: String): Either<ReservationError.GetWalletInfo, WalletInfo> = either {
        val wallet = walletService.getWalletInfo(code) ?: raise(ReservationError.WalletNotFound)
        val settings = appSettingsProvider.current
        WalletInfo(
            code = wallet.code,
            balance = wallet.balance,
            emailMatches = wallet.ownerEmail.equals(email, ignoreCase = true),
            seasonResetDay = settings.seasonResetDay,
            seasonResetMonth = settings.seasonResetMonth,
        )
    }

    private suspend fun resolveOwnerEmails(target: ReservationTarget): List<String> {
        val emails = mutableSetOf<String>()
        when (target) {
            is ReservationTarget.Instance -> {
                emails += target.event.ownerEmails
                if (target.event.seriesId != null) {
                    val seriesId = target.event.seriesId!!
                    emails += eventSeriesRepository.get(seriesId)?.ownerEmails ?: emptyList()
                }
                emails += eventDefinitionRepository.get(target.event.definitionId)?.ownerEmails ?: emptyList()
            }
            is ReservationTarget.Series -> {
                emails += target.series.ownerEmails
                emails += eventDefinitionRepository.get(target.series.definitionId)?.ownerEmails ?: emptyList()
            }
        }
        return emails.filter { it.isNotBlank() }
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

open class AuthenticatedReservationService(
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val reservationRepository: ReservationRepository,
    private val seriesLessonOptOutRepository: SeriesLessonOptOutRepository,
) : AuthenticatedReservationServiceInterface {

    /** Returns the authenticated caller's UUID from the JWT principal, or null if unavailable. */
    internal open suspend fun currentCallerUserId(): Uuid? {
        val idString = currentCall()
            ?.principal<JWTPrincipal>()
            ?.payload?.getClaim("id")?.asString()
            ?: return null
        return runCatching { Uuid.parse(idString) }.getOrNull()
    }
    override suspend fun getReservations(userId: Uuid): Either<ReservationError.GetAll, List<MyReservationListItem>> = either {
        val reservations = reservationRepository.getAll(userId)
            .filter { it.status != Reservation.Status.CANCELLED }
        if (reservations.isEmpty()) return@either emptyList()

        val instanceIds = reservations.mapNotNull { (it.reference as? Reference.Instance)?.id }
        val seriesIds = reservations.mapNotNull { (it.reference as? Reference.Series)?.id }
        val events = eventInstanceRepository.getAll(instanceIds).associateBy { it.id }
        val series = eventSeriesRepository.getAll(seriesIds).associateBy { it.id }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        reservations.mapNotNull { reservation ->
            when (val ref = reservation.reference) {
                is Reference.Instance -> {
                    val event = events[ref.id] ?: return@mapNotNull null
                    if (event.isCancelled || event.endDateTime <= now) return@mapNotNull null
                    reservation.toListItem(
                        title = event.title,
                        startDateTime = event.startDateTime,
                        isSeries = false,
                    )
                }
                is Reference.Series -> {
                    val seriesItem = series[ref.id] ?: return@mapNotNull null
                    if (seriesItem.endDate <= now.date) return@mapNotNull null
                    reservation.toListItem(
                        title = seriesItem.title,
                        startDateTime = LocalDateTime(seriesItem.startDate, LocalTime(0, 0)),
                        isSeries = true,
                    )
                }
            }
        }.sortedBy { it.startDateTime }
    }

    private fun Reservation.toListItem(title: String, startDateTime: LocalDateTime, isSeries: Boolean): MyReservationListItem =
        MyReservationListItem(
            id = id,
            eventTitle = title,
            startDateTime = startDateTime,
            seatCount = seatCount,
            totalPrice = totalPrice,
            status = status,
            paymentType = paymentType,
            variableSymbol = variableSymbol,
            isSeries = isSeries,
        )

    override suspend fun getSeriesReservationDetail(
        reservationId: Uuid,
    ): Either<ReservationError.GetDetail, SeriesReservationDetail> = either {
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) {
            ReservationError.ReservationNotFound
        }
        ensure(reservation.reference is Reference.Series) {
            ReservationError.ReservationNotFound
        }

        // Ownership check: only the owner may view their reservation details
        val callerUuid = currentCallerUserId()
        ensure(callerUuid != null && reservation.registeredUserId == callerUuid) {
            ReservationError.ReservationNotFound  // Don't reveal existence to non-owner
        }
        val seriesId = reservation.reference.id

        val instances = eventInstanceRepository.findBySeries(seriesId)
        val optOuts = seriesLessonOptOutRepository.findByReservation(reservationId)
        val optOutMap = optOuts.associateBy { it.instanceId }

        val lessons = instances.map { instance ->
            val optOut = optOutMap[instance.id]
            SeriesLessonItem(
                instanceId = instance.id,
                startDateTime = instance.startDateTime,
                endDateTime = instance.endDateTime,
                isCancelled = instance.isCancelled,
                isOptedOut = optOut != null,
                isLateCancellation = optOut?.isLateCancellation ?: false,
            )
        }

        SeriesReservationDetail(reservation = reservation, lessons = lessons)
    }
}