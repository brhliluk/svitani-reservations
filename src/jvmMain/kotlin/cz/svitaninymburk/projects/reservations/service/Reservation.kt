package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.error.WalletError
import cz.svitaninymburk.projects.reservations.event.calculateTotalPrice
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.INACTIVE_RESERVATION_STATUSES
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
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonsView
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.util.AuditSubject
import cz.svitaninymburk.projects.reservations.util.auditSubjectFor
import cz.svitaninymburk.projects.reservations.util.withAuditSubject
import cz.svitaninymburk.projects.reservations.util.captureEmailError
import cz.svitaninymburk.projects.reservations.util.currentCall
import cz.svitaninymburk.projects.reservations.util.PhoneNumber
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
import kotlin.time.Instant
import kotlin.uuid.Uuid


/**
 * Provoz běží v Praze a podle Prahy se počítá i uzávěrka omluvenek (18:00 den předem).
 * Produkce nemá připnuté TZ, takže currentSystemDefault() by na UTC hostu pustil
 * omluvenku z lekce, která už dvě hodiny běží.
 */
internal val OPT_OUT_TIMEZONE = TimeZone.of("Europe/Prague")

/**
 * Uzávěrka pro storno/omluvenku s nárokem na kredit: 18:00 den předem. Jediné
 * místo, kde to pravidlo žije — posílá se i klientovi, protože prohlížeč nemá
 * databázi časových pásem a sám by ji spočítal v zóně návštěvníka.
 */
internal fun refundDeadlineFor(start: LocalDateTime): Instant =
    start.date.minus(1, DateTimeUnit.DAY).atTime(18, 0).toInstant(OPT_OUT_TIMEZONE)

/**
 * Rezervaci bez účtu chrání jen znalost UUID — stejná laťka, jakou má odjakživa
 * zrušení celé rezervace. Registrovanou rezervaci smí měnit jen její majitel;
 * anonymnímu volajícímu se otevřít nesmí, jinak by přihlášení uživatelé měli
 * slabší ochranu než dnes.
 */
internal fun Reservation.isAccessibleBy(callerUserId: Uuid?): Boolean =
    registeredUserId == null || registeredUserId == callerUserId


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
    private val audit: AuditService = AuditService(InMemoryAuditRepository()),
    private val refundService: RefundService = RefundService(walletService, walletEmailService, appSettingsProvider),
    private val seriesLessonsReader: SeriesLessonsReader = SeriesLessonsReader(
        eventInstanceRepository,
        seriesLessonOptOutRepository,
    ),
    private val waitlistPromoter: WaitlistPromoter = WaitlistPromoter(
        eventInstanceRepository,
        eventSeriesRepository,
        reservationRepository,
        emailService,
        qrCodeService,
        appBaseUrl,
    ),
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

    private suspend fun isAdminCaller(): Boolean {
        val role = currentCall()
            ?.principal<JWTPrincipal>()
            ?.payload?.getClaim("role")?.asString()
        return role == User.Role.ADMIN.name
    }

    /**
     * Rezervace vznikají i bez přihlášení, takže se aktér nedá vždy vzít z JWT.
     * Admin zůstane adminem, jinak je to zákazník s adresou z formuláře.
     */
    private suspend fun actorFor(reservation: Reservation): AuditService.Actor =
        if (isAdminCaller()) audit.currentActor()
        else AuditService.Actor(AuditActorType.CUSTOMER, reservation.contactEmail)

    override suspend fun get(id: Uuid): Either<ReservationError.Get, Reservation> = either {
        reservationRepository.findById(id) ?: raise(ReservationError.ReservationNotFound)
    }

    override suspend fun getDetail(id: Uuid): Either<ReservationError.GetDetail, ReservationDetail> = either {
        val reservation = get(id).getOrElse { raise(ReservationError.ReservationNotFound) }

        val target: ReservationTarget? = when (val ref = reservation.reference) {
            is Reference.Instance -> eventInstanceRepository.get(ref.id)?.let { ReservationTarget.Instance(it) }
            is Reference.Series -> eventSeriesRepository.get(ref.id)?.let { ReservationTarget.Series(it) }
        }

        val waitlistPosition: Int? = if (reservation.status == Reservation.Status.WAITLISTED) {
            val position = reservationRepository.findByReference(reservation.reference)
                .filter { it.status == Reservation.Status.WAITLISTED }
                .sortedBy { it.createdAt }
                .indexOfFirst { it.id == reservation.id }
            if (position >= 0) position + 1 else null
        } else null

        ReservationDetail(
            reservation = reservation,
            target = target,
            accountNumber = qrCodeService.accountNumber,
            waitlistPosition = waitlistPosition,
            cancellationDeadline = target?.let { refundDeadlineFor(it.startDateTime) },
        )
    }

    override suspend fun getSeriesLessons(reservationId: Uuid): Either<ReservationError.GetDetail, SeriesLessonsView> = either {
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) { ReservationError.ReservationNotFound }
        ensure(reservation.reference is Reference.Series) { ReservationError.ReservationNotFound }
        // Neprozrazovat existenci cizí rezervace — proto všechno na ReservationNotFound.
        ensure(reservation.isAccessibleBy(currentCallerUserId())) { ReservationError.ReservationNotFound }

        val seriesId = reservation.reference.id
        val lessonRefundAmount = eventSeriesRepository.get(seriesId)?.lessonRefundAmount

        SeriesLessonsView(
            lessons = seriesLessonsReader.lessonsFor(reservationId, seriesId),
            paidAmount = reservation.paidAmount,
            alreadyRefunded = walletService.refundedForLessonOptOuts(reservationId),
            lessonRefundAmount = lessonRefundAmount,
            seatCount = reservation.seatCount,
            isAnonymousReservation = reservation.registeredUserId == null,
        )
    }


    override suspend fun reserveInstance(request: CreateInstanceReservationRequest, userId: Uuid?): Either<ReservationError.CreateReservation, Reservation> = either {

        val instance = ensureNotNull(eventInstanceRepository.get(request.eventInstanceId)) { ReservationError.ReservationNotFound }
        if (!isAdminCaller()) ensure(instance.isPublished) { ReservationError.ReservationNotFound }

        ensure(!instance.isCancelled) { ReservationError.EventCancelled }
        ensure(instance.endDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyFinished }
        ensure(instance.startDateTime > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { ReservationError.EventAlreadyStarted }
        ensure(!instance.isDeadlinePassed) { ReservationError.ReservationDeadlinePassed }

        ensure(request.seatCount >= 1) { ReservationError.InvalidSeatCount }
        ensure(instance.allowMultipleSeats || request.seatCount == 1) { ReservationError.MultipleSeatsNotAllowed }

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
        if (!isAdminCaller()) ensure(series.isPublished) { ReservationError.ReservationNotFound }

        ensure(!series.isDeadlinePassed) { ReservationError.ReservationDeadlinePassed }

        ensure(request.seatCount >= 1) { ReservationError.InvalidSeatCount }
        ensure(series.allowMultipleSeats || request.seatCount == 1) { ReservationError.MultipleSeatsNotAllowed }

        val isReserved = eventSeriesRepository.attemptToReserveSpots(series.id, request.seatCount)
        ensure(isReserved) { ReservationError.CapacityExceeded }

        val seriesTarget = ReservationTarget.Series(series.copy(lessonCount = eventInstanceRepository.countActiveBySeries(series.id).toInt()))

        createReservationFlow(
            reference = Reference.Series(series.id),
            userId = userId,
            requestData = request,
            pricePerSeat = series.price,
            target = seriesTarget,
            walletCode = request.walletCode,
        )
    }

    override suspend fun joinWaitlistInstance(
        request: CreateInstanceReservationRequest,
        userId: Uuid?,
    ): Either<ReservationError.CreateReservation, Reservation> = either {
        val instance = ensureNotNull(eventInstanceRepository.get(request.eventInstanceId)) { ReservationError.ReservationNotFound }
        if (!isAdminCaller()) ensure(instance.isPublished) { ReservationError.ReservationNotFound }
        ensure(!instance.isCancelled) { ReservationError.EventCancelled }

        ensure(instance.isFull) { ReservationError.EventNotFull }
        ensure(instance.hasWaitlist) { ReservationError.WaitlistNotAvailable }

        val claimed = eventInstanceRepository.attemptToReserveWaitlistSpot(instance.id)
        ensure(claimed) { ReservationError.WaitlistFull }

        joinWaitlistFlow(
            reference = Reference.Instance(instance.id),
            userId = userId,
            requestData = request,
            pricePerSeat = instance.price,
            target = ReservationTarget.Instance(instance),
        )
    }

    override suspend fun joinWaitlistSeries(
        request: CreateSeriesReservationRequest,
        userId: Uuid?,
    ): Either<ReservationError.CreateReservation, Reservation> = either {
        val series = ensureNotNull(eventSeriesRepository.get(request.eventSeriesId)) { ReservationError.ReservationNotFound }
        if (!isAdminCaller()) ensure(series.isPublished) { ReservationError.ReservationNotFound }

        ensure(series.isFull) { ReservationError.EventNotFull }
        ensure(series.hasWaitlist) { ReservationError.WaitlistNotAvailable }

        val claimed = eventSeriesRepository.attemptToReserveWaitlistSpot(series.id)
        ensure(claimed) { ReservationError.WaitlistFull }

        joinWaitlistFlow(
            reference = Reference.Series(series.id),
            userId = userId,
            requestData = request,
            pricePerSeat = series.price,
            target = ReservationTarget.Series(series),
        )
    }

    private suspend fun Raise<ReservationError.CreateReservation>.joinWaitlistFlow(
        reference: Reference,
        userId: Uuid?,
        requestData: ReservationRequestData,
        pricePerSeat: Double,
        target: ReservationTarget,
    ): Reservation {
        val reservation = Reservation(
            id = Uuid.random(),
            reference = reference,
            registeredUserId = userId,
            seatCount = requestData.seatCount,
            contactName = requestData.contactName,
            contactEmail = requestData.contactEmail,
            contactPhone = PhoneNumber.normalize(requestData.contactPhone) ?: requestData.contactPhone,
            paymentType = requestData.paymentType,
            customValues = requestData.customValues,
            totalPrice = calculateTotalPrice(
                basePrice = pricePerSeat,
                seatCount = requestData.seatCount,
                customFields = target.customFields,
                customValues = requestData.customValues,
            ),
            status = Reservation.Status.WAITLISTED,
            createdAt = Clock.System.now(),
            variableSymbol = null,
            locale = requestData.locale,
        )

        val saved = reservationRepository.save(reservation)
        logger.info("Waitlist signup id=${saved.id} ref=$reference seats=${saved.seatCount} status=${saved.status}")

        emailService.sendWaitlistConfirmation(
            toEmail = saved.contactEmail,
            eventTitle = target.title,
            contactName = saved.contactName,
            reservationId = saved.id,
            locale = saved.locale,
        ).onLeft { captureEmailError(logger, "Failed to send waitlist confirmation email for reservation ${saved.id}: $it") }

        return saved
    }

    private suspend fun Raise<ReservationError.CreateReservation>.createReservationFlow(
        reference: Reference,
        userId: Uuid?,
        requestData: ReservationRequestData,
        pricePerSeat: Double,
        target: ReservationTarget,
        walletCode: String? = null,
    ): Reservation {
        val variableSymbol = reservationRepository.generateUniqueVariableSymbol()
            ?: raise(ReservationError.SystemError("Unable to generate unique Variable Symbol"))

        val totalPrice = calculateTotalPrice(
            basePrice = pricePerSeat,
            seatCount = requestData.seatCount,
            customFields = target.customFields,
            customValues = requestData.customValues,
        )
        // Akce zdarma nemá kam posílat platbu, takže rezervace vzniká rovnou
        // potvrzená: jinak by detail nabízel QR kód na 0 Kč a admin přehled by ji
        // vedl mezi nezaplacenými (Admin.kt), včetně zbytečného dotazování FIO
        // (hasPendingReservations v repository/reservation/Database.kt).
        val isFree = totalPrice <= 0.0

        val reservation = Reservation(
            id = Uuid.random(),
            reference = reference,
            registeredUserId = userId,
            seatCount = requestData.seatCount,
            contactName = requestData.contactName,
            contactEmail = requestData.contactEmail,
            contactPhone = PhoneNumber.normalize(requestData.contactPhone) ?: requestData.contactPhone,
            paymentType = if (isFree) PaymentInfo.Type.FREE else requestData.paymentType,
            customValues = requestData.customValues,
            totalPrice = totalPrice,
            status = if (isFree) Reservation.Status.CONFIRMED else Reservation.Status.PENDING_PAYMENT,
            createdAt = Clock.System.now(),
            variableSymbol = variableSymbol,
            locale = requestData.locale,
        )

        var savedReservation = reservationRepository.save(reservation)
        logger.info(
            "Reservation created id=${savedReservation.id} ref=$reference seats=${savedReservation.seatCount} " +
                "status=${savedReservation.status} payment=${savedReservation.paymentType} vs=$variableSymbol"
        )

        val subject = auditSubjectFor(target, savedReservation.id)
        audit.record(
            type = AuditEventType.RESERVATION_CREATED,
            actor = actorFor(savedReservation),
            subjectLabel = savedReservation.contactName,
            seriesId = subject.seriesId,
            instanceId = subject.instanceId,
            reservationId = savedReservation.id,
            amount = savedReservation.totalPrice,
            detail = "${savedReservation.seatCount}× místo, stav ${savedReservation.status}, VS $variableSymbol",
        )

        // Odsud dál se posílají maily; subjekt v kontextu jim dá vazbu na akci,
        // kterou samy neznají — nesou jen adresu a název.
        return withAuditSubject(subject) {
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
                        ).onLeft { captureEmailError(logger, "Failed to send wallet applied email to ${reservation.contactEmail}: $it") }
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
            ).onLeft { captureEmailError(logger, "Failed to send confirmation email for reservation ${savedReservation.id}: $it") }

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
                    ).onLeft { captureEmailError(logger, "Failed to send owner reservation email to $email: $it") }
                }
            }

            paymentTrigger.notifyNewReservation()

            savedReservation
        }
    }

    override suspend fun cancelReservation(
        reservationId: Uuid,
        instanceId: Uuid?,
        walletCode: String?,
        force: Boolean,
    ): Either<ReservationError.CancelReservation, CancellationResult> = either {
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) { ReservationError.ReservationNotFound }

        if (instanceId != null) {
            // Omluvenka z jedné lekce. Rezervaci bez účtu chrání jen znalost UUID —
            // stejná laťka jako u zrušení celé rezervace níž; registrovanou její majitel.
            ensure(reservation.isAccessibleBy(currentCallerUserId())) {
                ReservationError.ReservationNotFound  // Don't reveal existence to non-owner
            }
            // Zrušená rezervace už kredit dostala a čekatel nedrží místo, které by šlo
            // uvolnit — bez téhle kontroly jde po stornu inkasovat kredit ještě jednou.
            ensure(reservation.status !in INACTIVE_RESERVATION_STATUSES) { ReservationError.ReservationNotFound }

            ensure(reservation.reference is Reference.Series) { ReservationError.NotASeriesReservation }
            val seriesId = reservation.reference.id

            val instance = ensureNotNull(eventInstanceRepository.get(instanceId)) {
                ReservationError.InstanceNotInSeries
            }
            ensure(instance.seriesId == seriesId) { ReservationError.InstanceNotInSeries }
            ensure(!instance.isCancelled) { ReservationError.EventAlreadyFinished }
            ensure(
                Clock.System.now() < instance.startDateTime.toInstant(OPT_OUT_TIMEZONE)
            ) { ReservationError.EventAlreadyStarted }
            ensure(
                seriesLessonOptOutRepository.findByReservationAndInstance(reservationId, instanceId) == null
            ) { ReservationError.AlreadyOptedOut }

            val now = Clock.System.now()
            val isLate = now > refundDeadlineFor(instance.startDateTime)

            // Kredit i peněženku řešíme JEŠTĚ PŘED zápisem omluvenky. Neshoda e-mailu
            // u peněženky je uživatelská chyba k opakování — kdyby se vyhodila až po
            // zápisu, byl by člověk odhlášený, čekatel posunutý a druhý pokus by spadl
            // na AlreadyOptedOut.
            val series = eventSeriesRepository.get(seriesId)
            val perLesson = (series?.lessonRefundAmount ?: 0.0) * reservation.seatCount
            // Skutečně vyplacené částky z účetnictví peněženky, ne odhad z počtu
            // omluvenek — kdo se odhlásil ještě před zaplacením, nedostal nic a
            // nesmí mu to ukrajovat ze stropu.
            val alreadyRefunded = walletService.refundedForLessonOptOuts(reservationId)
            val refundAmount: Double = when {
                reservation.paidAmount <= 0.0 -> 0.0  // nothing was paid, no refund
                isLate -> 0.0  // late cancellation, no refund
                // Souhrn omluvenek nesmí přerůst zaplacenou částku — lessonRefundAmount
                // je volná admin hodnota nezávislá na ceně kurzu.
                else -> minOf(perLesson, reservation.paidAmount - alreadyRefunded).coerceAtLeast(0.0)
            }
            val wallet: Wallet? =
                if (refundAmount > 0.0) resolveWalletFor(reservation, walletCode, force) else null

            // saveIfAbsent, ne save — kontrola výš běží v jiné transakci, takže
            // dvojklik by jinak uložil dvě omluvenky a místo by se odečetlo dvakrát.
            ensureNotNull(
                seriesLessonOptOutRepository.saveIfAbsent(
                    SeriesLessonOptOut(
                        id = Uuid.random(),
                        reservationId = reservationId,
                        instanceId = instanceId,
                        optedOutAt = now,
                        isLateCancellation = isLate,
                    )
                )
            ) { ReservationError.AlreadyOptedOut }
            // Odečtem je nově sama existence omluvenky — uložený čítač lekce drží
            // jen přímé rezervace, takže by ho tenhle dekrement stáhl do záporu.
            waitlistPromoter.promote(Reference.Instance(instanceId), freedSeats = reservation.seatCount)

            audit.record(
                type = AuditEventType.RESERVATION_LESSON_OPT_OUT,
                actor = actorFor(reservation),
                subjectLabel = reservation.contactName,
                seriesId = seriesId,
                instanceId = instanceId,
                reservationId = reservationId,
                amount = refundAmount,
                detail = if (isLate) "pozdní omluvenka, bez vrácení kreditu" else "omluvenka z lekce",
            )

            withAuditSubject(
                AuditSubject(
                    seriesId = seriesId,
                    instanceId = instanceId,
                    reservationId = reservationId,
                    label = instance.title,
                )
            ) {
                emailService.sendLessonOptOutNotice(
                    toEmail = reservation.contactEmail,
                    eventTitle = instance.title,
                    lessonDate = instance.startDateTime.date,
                    isLateCancellation = isLate,
                    locale = reservation.locale,
                ).onLeft { captureEmailError(logger, "Failed to send opt-out email to ${reservation.contactEmail}: $it") }

                val ownerEmails = parseOwnerEmails(instance.ownerEmails)
                ownerEmails.forEach { ownerEmail ->
                    lectorEmailService.sendLectorLessonOptOutNotification(
                        lectorEmail = ownerEmail,
                        contactName = reservation.contactName,
                        eventTitle = instance.title,
                        lessonDate = instance.startDateTime.date,
                        isLateCancellation = isLate,
                        locale = reservation.locale,
                    ).onLeft { captureEmailError(logger, "Failed to send owner opt-out email to $ownerEmail: $it") }
                }
            }

            if (wallet != null) {
                val outcome = refundService.refundFixedAmount(
                    wallet, reservation, refundAmount, WalletTransactionReason.LESSON_OPT_OUT_REFUND
                )
                if (outcome != null) CancellationResult(walletCode = outcome.walletCode, walletCreditAmount = outcome.creditedAmount)
                else CancellationResult()
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
            logger.info("Reservation cancelled id=${reservation.id} ref=${reservation.reference} seats=${reservation.seatCount}")

            if (target == null) {
                // Referenced event was deleted — still cancel the reservation, skip side effects
                CancellationResult()
            } else {
                // decrementOccupiedSpots vrací jen zúžený uložený sloupec (přímé
                // rezervace bez zátěže kurzu) — pro e-mail lektorovi to čte znovu přes
                // repository, které při čtení dopočítá zátěž kurzu (SeriesAwareEventInstanceRepository),
                // aby se obsazenost lekce shodovala s potvrzovacím e-mailem účastníkovi (viz výš).
                // Čekatel v pořadníku žádné místo nedrží — drží jen přihlášku. Strhnout
                // mu occupiedSpots by čítač stáhlo do záporu a posouvat není koho,
                // protože se žádné místo neuvolnilo. Ubere se jen jedna přihláška
                // z pořadníku, protože po přihláškách se pořadník i napočítává
                // (attemptToReserveWaitlistSpot zvedá o 1).
                val wasWaitlisted = reservation.status == Reservation.Status.WAITLISTED

                val updatedSpots = if (wasWaitlisted) {
                    when (reservation.reference) {
                        is Reference.Instance -> eventInstanceRepository.decrementOccupiedWaitlist(reservation.reference.id, 1)
                        is Reference.Series -> eventSeriesRepository.decrementOccupiedWaitlist(reservation.reference.id, 1)
                    }
                    when (target) {
                        is ReservationTarget.Instance -> target.event.occupiedSpots
                        is ReservationTarget.Series -> target.series.occupiedSpots
                    }
                } else {
                    // decrementOccupiedSpots vrací jen zúžený uložený sloupec (přímé
                    // rezervace bez zátěže kurzu) — pro e-mail lektorovi to čte znovu přes
                    // repository, které při čtení dopočítá zátěž kurzu (SeriesAwareEventInstanceRepository),
                    // aby se obsazenost lekce shodovala s potvrzovacím e-mailem účastníkovi (viz výš).
                    when (reservation.reference) {
                        is Reference.Instance -> {
                            eventInstanceRepository.decrementOccupiedSpots(reservation.reference.id, reservation.seatCount)
                            eventInstanceRepository.get(reservation.reference.id)?.occupiedSpots
                        }
                        is Reference.Series -> {
                            eventSeriesRepository.decrementOccupiedSpots(reservation.reference.id, reservation.seatCount)
                            eventSeriesRepository.get(reservation.reference.id)?.occupiedSpots
                        }
                    } ?: when (target) {
                        is ReservationTarget.Instance -> (target.event.occupiedSpots - reservation.seatCount).coerceAtLeast(0)
                        is ReservationTarget.Series -> (target.series.occupiedSpots - reservation.seatCount).coerceAtLeast(0)
                    }
                }

                if (!wasWaitlisted) {
                    waitlistPromoter.promote(reservation.reference, freedSeats = reservation.seatCount)
                }

                val cancelSubject = auditSubjectFor(target, cancelledReservation.id)
                audit.record(
                    type = AuditEventType.RESERVATION_CANCELLED,
                    actor = actorFor(cancelledReservation),
                    subjectLabel = cancelledReservation.contactName,
                    seriesId = cancelSubject.seriesId,
                    instanceId = cancelSubject.instanceId,
                    reservationId = cancelledReservation.id,
                    amount = cancelledReservation.paidAmount,
                    detail = if (wasWaitlisted) "storno přihlášky z pořadníku" else "storno celé rezervace",
                )

                val customerEmailResult = withAuditSubject(cancelSubject) {
                    emailService.sendCancellationNotice(cancelledReservation.contactEmail, target.title, cancelledReservation.id, cancelledReservation.locale)
                }

                val ownerEmails = resolveOwnerEmails(target)
                if (ownerEmails.isNotEmpty()) {
                    val capacity = when (target) {
                        is ReservationTarget.Instance -> target.event.capacity
                        is ReservationTarget.Series -> target.series.capacity
                    }
                    withAuditSubject(cancelSubject) {
                        ownerEmails.forEach { email ->
                            lectorEmailService.sendLectorCancellationNotification(
                                lectorEmail = email,
                                contactName = cancelledReservation.contactName,
                                eventTitle = target.title,
                                seatCount = cancelledReservation.seatCount,
                                occupiedSpots = updatedSpots,
                                capacity = capacity,
                                locale = cancelledReservation.locale,
                            ).onLeft { captureEmailError(logger, "Failed to send owner cancellation email to $email: $it") }
                        }
                    }
                }

                customerEmailResult.mapLeft { ReservationError.FailedToSendCancellationEmail(it) }.bind()

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
                        .mapLeft { e ->
                            when (e) {
                                WalletError.NotFound -> ReservationError.WalletNotFound
                                WalletError.EmailMismatch -> ReservationError.WalletEmailMismatch
                            }
                        }
                        when (resolved) {
                            is Either.Left -> raise(resolved.value)
                            is Either.Right -> resolved.value
                        }
                    }
                    val outcome = refundService.refundWholeReservation(wallet, reservation)
                    if (outcome != null) CancellationResult(walletCode = outcome.walletCode, walletCreditAmount = outcome.creditedAmount)
                    else CancellationResult()
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
        return parseOwnerEmails(emails.toList())
    }

    /**
     * Peněženka, do které se má vrátit kredit. Registrovaná rezervace má peněženku
     * svázanou s účtem; u rezervace bez účtu se jde podle kódu, který člověk zadal,
     * a bez něj podle kontaktního e-mailu.
     */
    private suspend fun Raise<ReservationError.CancelReservation>.resolveWalletFor(
        reservation: Reservation,
        walletCode: String?,
        force: Boolean,
    ): Wallet {
        val registeredUserId = reservation.registeredUserId
        if (registeredUserId != null) {
            return walletService.findOrCreateForRegisteredUser(registeredUserId, reservation.contactEmail)
        }
        return walletService.resolveAnonymousWalletForRepeatedRefund(walletCode, reservation.contactEmail, force)
            .mapLeft { e ->
                when (e) {
                    WalletError.NotFound -> ReservationError.WalletNotFound
                    WalletError.EmailMismatch -> ReservationError.WalletEmailMismatch
                }
            }
            .bind()
    }

}

open class AuthenticatedReservationService(
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val reservationRepository: ReservationRepository,
) : AuthenticatedReservationServiceInterface {

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

}