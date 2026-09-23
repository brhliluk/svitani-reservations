package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.error.DuplicateScope
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.error.WalletError
import cz.svitaninymburk.projects.reservations.event.calculateTotalPrice
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.i18n.LectorTarget
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.INACTIVE_RESERVATION_STATUSES
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ACTIVE_SIGNUP_STATUSES
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.CancellationResult
import cz.svitaninymburk.projects.reservations.reservation.ClaimResult
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
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.claim.InMemoryReservationClaimTokenRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.user.UserRepository
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

/**
 * Akce nebo kurz rezervace ještě běží. Jediné místo, kde ta laťka žije — drží ji
 * „Moje rezervace“ i přivlastnění, aby tlačítko nepřipsalo k účtu rezervaci, která
 * se pak v seznamu neobjeví.
 *
 * U kurzu rozhoduje jeho konec, ne jednotlivé lekce: kurz, kterému část lekcí už
 * proběhla, pořád běží. Počítá se v provozní zóně ze stejného důvodu jako uzávěrka
 * omluvenek — produkce nemá připnuté TZ a currentSystemDefault() by na UTC hostu
 * posunul hranici o dvě hodiny.
 */
internal fun ReservationTarget.isStillRunning(now: Instant): Boolean {
    val cancelled = when (this) {
        is ReservationTarget.Instance -> event.isCancelled
        is ReservationTarget.Series -> series.isCancelled
    }
    return !cancelled && now < endDateTime.toInstant(OPT_OUT_TIMEZONE)
}

/**
 * Rezervaci bez účtu si smí připsat jen přihlášený uživatel se stejným kontaktním
 * e-mailem. Adresy se ukládají tak, jak je člověk napsal, takže se normalizují
 * stejně jako v [DuplicateReservationDetector].
 *
 * Neřeší, jestli akce ještě běží — to je [isStillRunning], protože na to volající
 * potřebuje načtený cíl rezervace.
 */
internal fun Reservation.isClaimableBy(callerUserId: Uuid?, callerEmail: String?): Boolean {
    if (callerUserId == null || callerEmail == null) return false
    if (registeredUserId != null) return false
    if (status !in ACTIVE_SIGNUP_STATUSES) return false
    return contactEmail.trim().equals(callerEmail.trim(), ignoreCase = true)
}

/**
 * Odmítne rezervaci, na kterou už stejný e-mail přihlášku má — ale jen napoprvé.
 * Jakmile uživatel varování odklikne, [acknowledged] je `true` a detektor se ani nespouští.
 *
 * Volá se **před** zabráním kapacity: `attemptToReserveSpots` ukousne místo atomicky
 * hned a odmítnutý pokus by ho už nevrátil.
 */
internal suspend inline fun Raise<ReservationError.CreateReservation>.ensureNoDuplicate(
    acknowledged: Boolean,
    detect: () -> DuplicateScope?,
) {
    if (acknowledged) return
    detect()?.let { raise(ReservationError.AlreadyReserved(it)) }
}


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
    /**
     * Jen pro e-mail přihlášeného účtu (příznak `claimable`). Bere se z databáze,
     * ne z JWT — změna e-mailu token nepřevydává, takže by v něm zůstala stará adresa.
     */
    private val userRepository: UserRepository = InMemoryUserRepository(),
    /**
     * Kudy odchází maily. Výchozí [InlineEmailDispatcher] drží dosavadní chování
     * pro testy, které hned po operaci tvrdí, co se odeslalo; v běhu DI dosadí
     * [BackgroundEmailDispatcher], aby zákazník nečekal na SMTP.
     */
    private val emailDispatcher: EmailDispatcher = InlineEmailDispatcher,
    private val audit: AuditService = AuditService(InMemoryAuditRepository()),
    private val refundService: RefundService = RefundService(walletService, walletEmailService, appSettingsProvider),
    private val seriesLessonsReader: SeriesLessonsReader = SeriesLessonsReader(
        eventInstanceRepository,
        seriesLessonOptOutRepository,
    ),
    private val duplicateDetector: DuplicateReservationDetector = DuplicateReservationDetector(
        reservationRepository,
        eventInstanceRepository,
    ),
    private val waitlistPromoter: WaitlistPromoter = WaitlistPromoter(
        eventInstanceRepository,
        eventSeriesRepository,
        reservationRepository,
        emailService,
        qrCodeService,
        appBaseUrl,
    ),
    /**
     * Uplatnění odkazu z mailu visí na téhle službě jen kvůli routingu — je to jediné
     * rozhraní pod volitelnou autentizací, a odkaz musí jít otevřít i bez session.
     */
    private val claimService: ReservationClaimService = ReservationClaimService(
        reservationRepository,
        eventInstanceRepository,
        eventSeriesRepository,
        userRepository,
        InMemoryReservationClaimTokenRepository(),
        emailService,
        appBaseUrl,
        audit,
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

    /** E-mail přihlášeného účtu podle databáze; `null`, když nikdo přihlášený není. */
    private suspend fun currentCallerEmail(callerUserId: Uuid?): String? =
        callerUserId?.let { userRepository.findById(it)?.email }

    /** Stejný testovací seam jako [currentCallerUserId] — admin obchází kontroly přístupu. */
    internal open suspend fun isAdminCaller(): Boolean {
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

    override suspend fun confirmReservationClaim(token: String): Either<ReservationError.ConfirmClaim, ClaimResult> =
        claimService.confirmClaim(token, Clock.System.now())

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

        val callerUserId = currentCallerUserId()
        val claimable = reservation.isClaimableBy(callerUserId, currentCallerEmail(callerUserId)) &&
            target?.isStillRunning(Clock.System.now()) == true

        ReservationDetail(
            reservation = reservation,
            target = target,
            accountNumber = qrCodeService.accountNumber,
            waitlistPosition = waitlistPosition,
            cancellationDeadline = target?.let { refundDeadlineFor(it.startDateTime) },
            claimable = claimable,
        )
    }

    override suspend fun getSeriesLessons(reservationId: Uuid): Either<ReservationError.GetDetail, SeriesLessonsView> = either {
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) { ReservationError.ReservationNotFound }
        ensure(reservation.reference is Reference.Series) { ReservationError.ReservationNotFound }
        // Neprozrazovat existenci cizí rezervace — proto všechno na ReservationNotFound.
        // Admin vidí i cizí: z historie v adminu vede proklik na /reservation/{id}
        // a bez tohohle by se mu sekce lekcí u registrovaného uživatele nenačetla.
        ensure(isAdminCaller() || reservation.isAccessibleBy(currentCallerUserId())) { ReservationError.ReservationNotFound }

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

        // Musí být před zabráním kapacity — attemptToReserveSpots ukousne místo hned
        // a odmítnutý pokus by ho už nevrátil.
        ensureNoDuplicate(request.acknowledgedDuplicate) { duplicateDetector.forInstance(instance, request.contactEmail) }

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

        ensureNoDuplicate(request.acknowledgedDuplicate) { duplicateDetector.forSeries(series.id, request.contactEmail) }

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

        ensureNoDuplicate(request.acknowledgedDuplicate) { duplicateDetector.forInstance(instance, request.contactEmail) }

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

        ensureNoDuplicate(request.acknowledgedDuplicate) { duplicateDetector.forSeries(series.id, request.contactEmail) }

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

        val subject = auditSubjectFor(target, saved.id)
        audit.record(
            type = AuditEventType.RESERVATION_WAITLIST_JOINED,
            actor = actorFor(saved),
            subjectLabel = saved.contactName,
            seriesId = subject.seriesId,
            instanceId = subject.instanceId,
            reservationId = saved.id,
            amount = saved.totalPrice,
            detail = "${saved.seatCount}× místo",
        )

        return withAuditSubject(subject) {
            emailDispatcher.dispatch {
                emailService.sendWaitlistConfirmation(
                    toEmail = saved.contactEmail,
                    eventTitle = target.title,
                    contactName = saved.contactName,
                    reservationId = saved.id,
                    locale = saved.locale,
                ).onLeft { captureEmailError(logger, "Failed to send waitlist confirmation email for reservation ${saved.id}: $it") }
            }

            saved
        }
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
            paymentType = if (isFree) PaymentType.FREE else requestData.paymentType,
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
                                paymentType = if (fullyPaid) PaymentType.FREE else reservation.paymentType,
                            )
                        )
                        emailDispatcher.dispatch {
                            walletEmailService.sendWalletApplied(
                                toEmail = reservation.contactEmail,
                                walletCode = wallet.code,
                                deductedAmount = deductAmount,
                                remainingBalance = wallet.balance - deductAmount,
                                locale = reservation.locale,
                            ).onLeft { captureEmailError(logger, "Failed to send wallet applied email to ${reservation.contactEmail}: $it") }
                        }
                    }
                }
                // If wallet validation fails (not found / empty), silently ignore and proceed without wallet
            }

            // Odsud dál už jen rozesílání. Běží mimo požadavek, protože na výsledku
            // nic nezávisí (jen se loguje) a SMTP by jinak držel zákazníka na
            // formuláři sekundy — viz [EmailDispatcher]. Stav rezervace se proto
            // zafixuje teď; `savedReservation` je var a wallet ji výš mohl přepsat.
            val confirmed = savedReservation
            emailDispatcher.dispatch {
                val qrImage: ByteArray? = if (confirmed.paymentType == PaymentType.BANK_TRANSFER) {
                    qrCodeService.generateQrPng(confirmed, target)
                } else null

                val icalBytes = when (target) {
                    is ReservationTarget.Instance -> ICalGenerator.forInstance(target.event, confirmed.id, appBaseUrl)
                    is ReservationTarget.Series -> ICalGenerator.forSeries(target.series, confirmed.id, appBaseUrl)
                }.toByteArray(Charsets.UTF_8)

                emailService.sendReservationConfirmation(
                    toEmail = confirmed.contactEmail,
                    reservation = confirmed,
                    target = target,
                    bankAccount = qrCodeService.accountNumber,
                    qrCodeImage = qrImage,
                    icalBytes = icalBytes,
                ).onLeft { captureEmailError(logger, "Failed to send confirmation email for reservation ${confirmed.id}: $it") }

                val ownerEmails = resolveOwnerEmails(target)
                if (ownerEmails.isNotEmpty()) {
                    val newOccupiedSpots = when (target) {
                        is ReservationTarget.Instance -> target.event.occupiedSpots + confirmed.seatCount
                        is ReservationTarget.Series -> target.series.occupiedSpots + confirmed.seatCount
                    }
                    val capacity = when (target) {
                        is ReservationTarget.Instance -> target.event.capacity
                        is ReservationTarget.Series -> target.series.capacity
                    }
                    ownerEmails.forEach { email ->
                        lectorEmailService.sendLectorReservationNotification(
                            lectorEmail = email,
                            contactName = confirmed.contactName,
                            contactEmail = confirmed.contactEmail,
                            contactPhone = confirmed.contactPhone,
                            seatCount = confirmed.seatCount,
                            eventTitle = target.title,
                            target = target.forLector(),
                            occupiedSpots = newOccupiedSpots,
                            capacity = capacity,
                            locale = confirmed.locale,
                        ).onLeft { captureEmailError(logger, "Failed to send owner reservation email to $email: $it") }
                    }
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
                emailDispatcher.dispatch {
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

            // Stejná laťka jako u omluvenky z lekce výš: rezervaci bez účtu chrání
            // znalost UUID, registrovanou její majitel. Bez téhle brány zruší
            // rezervaci registrovaného kdokoli s odkazem — a protože se kredit posílá
            // do peněženky účtu, vrátil by se mu v odpovědi i její kód, se kterým jde
            // zůstatek utratit (walletService.validateForReservation řeší jen existenci
            // kódu a zůstatek).
            //
            // Admin ruší cizí rezervace z administrace touhle samou cestou
            // (ui/admin/**/usecase volá ReservationServiceInterface.cancelReservation),
            // takže pro něj platí výjimka jako u getSeriesLessons.
            ensure(isAdminCaller() || reservation.isAccessibleBy(currentCallerUserId())) {
                ReservationError.ReservationNotFound  // Don't reveal existence to non-owner
            }

            // Zrušit jde jen jednou. Tlačítko sice po stornu zmizí, ale RPC je pod
            // `optional = true` a druhé volání by prošlo celou cestou znovu: připsalo
            // by do peněženky celý paidAmount podruhé, znovu strhlo místa (čítač do
            // záporu) a poslalo druhý storno e-mail.
            ensure(reservation.status != Reservation.Status.CANCELLED) { ReservationError.AlreadyCancelled }

            val target: ReservationTarget? = when (reservation.reference) {
                is Reference.Instance -> eventInstanceRepository.get(reservation.reference.id)?.let { ReservationTarget.Instance(it) }
                is Reference.Series -> eventSeriesRepository.get(reservation.reference.id)?.let { ReservationTarget.Series(it) }
            }

            // Zákazníkovi zavře storno začátek akce — u kurzu je to jeho první den,
            // takže rozjetý kurz si sám odhlásit nemůže. Admin tuhle zeď nemá:
            // z administrace se odhlašují i lidi, co odpadli v půlce kurzu. O peníze
            // nejde, kredit se dole stejně řídí uzávěrkou (18:00 den předem), takže
            // pozdní storno nevrací nic.
            if (target != null && !isAdminCaller()) {
                ensure(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) < target.startDateTime) { ReservationError.EventAlreadyStarted }
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

                // Mail zákazníkovi jde synchronně jen proto, aby se dalo říct, jestli
                // odešel. Rezervace už je v tu chvíli zrušená a místo uvolněné, takže
                // selhání SMTP (Gmail občas dočasně odmítne s 451) nesmí storno shodit —
                // zákazník by viděl chybu u operace, která proběhla, a zkoušel by ji znovu.
                // V historii zůstane jako FAILURE a admin ho může poslat znovu.
                val cancellationEmailFailed = withAuditSubject(cancelSubject) {
                    emailService.sendCancellationNotice(cancelledReservation.contactEmail, target.title, cancelledReservation.id, cancelledReservation.locale)
                }.onLeft { captureEmailError(logger, "Failed to send cancellation email to ${cancelledReservation.contactEmail}: $it") }
                    .isLeft()

                val ownerEmails = resolveOwnerEmails(target)
                if (ownerEmails.isNotEmpty()) {
                    val capacity = when (target) {
                        is ReservationTarget.Instance -> target.event.capacity
                        is ReservationTarget.Series -> target.series.capacity
                    }
                    withAuditSubject(cancelSubject) {
                        emailDispatcher.dispatch {
                            ownerEmails.forEach { email ->
                                lectorEmailService.sendLectorCancellationNotification(
                                    lectorEmail = email,
                                    contactName = cancelledReservation.contactName,
                                    eventTitle = target.title,
                                    target = target.forLector(),
                                    seatCount = cancelledReservation.seatCount,
                                    occupiedSpots = updatedSpots,
                                    capacity = capacity,
                                    locale = cancelledReservation.locale,
                                ).onLeft { captureEmailError(logger, "Failed to send owner cancellation email to $email: $it") }
                            }
                        }
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
                    if (outcome != null) CancellationResult(walletCode = outcome.walletCode, walletCreditAmount = outcome.creditedAmount, cancellationEmailFailed = cancellationEmailFailed)
                    else CancellationResult(cancellationEmailFailed = cancellationEmailFailed)
                } else {
                    CancellationResult(cancellationEmailFailed = cancellationEmailFailed)
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

    /**
     * Lekce i celý kurz se jmenují stejně a každý hlásí obsazenost z jiné kapacity;
     * bez tohoto rozlišení lektor z mailu nepozná, čeho se číslo týká.
     */
    private fun ReservationTarget.forLector(): LectorTarget = when (this) {
        is ReservationTarget.Instance -> LectorTarget.Occasion(event.startDateTime)
        is ReservationTarget.Series -> LectorTarget.Course(
            startDate = series.startDate,
            endDate = series.endDate,
            lessonCount = series.lessonCount,
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
    private val userRepository: UserRepository = InMemoryUserRepository(),
    private val audit: AuditService = AuditService(InMemoryAuditRepository()),
    private val claimService: ReservationClaimService = ReservationClaimService(
        reservationRepository,
        eventInstanceRepository,
        eventSeriesRepository,
        userRepository,
        InMemoryReservationClaimTokenRepository(),
        ConsoleEmailService(),
        appBaseUrl = "",
        audit = audit,
    ),
) : AuthenticatedReservationServiceInterface {

    private val logger = KtorSimpleLogger(this::class.jvmName)

    /** Volající z JWT. Testy si ho podstrčí — stejný seam jako v [ReservationService]. */
    internal open suspend fun currentCallerUserId(): Uuid? {
        val idString = currentCall()
            ?.principal<JWTPrincipal>()
            ?.payload?.getClaim("id")?.asString()
            ?: return null
        return runCatching { Uuid.parse(idString) }.getOrNull()
    }

    override suspend fun countClaimableReservations(): Either<ReservationError.GetAll, Int> = either {
        val callerUserId = currentCallerUserId() ?: raise(ReservationError.FailedToGetAllReservations)
        claimService.countClaimable(callerUserId, Clock.System.now())
    }

    override suspend fun requestReservationClaim(): Either<ReservationError.RequestClaim, Int> = either {
        val callerUserId = currentCallerUserId() ?: raise(ReservationError.NothingToClaim)
        claimService.requestClaim(callerUserId, Clock.System.now()).bind()
    }

    override suspend fun getReservations(userId: Uuid): Either<ReservationError.GetAll, List<MyReservationListItem>> = either {
        val reservations = reservationRepository.getAll(userId)
            .filter { it.status != Reservation.Status.CANCELLED }
        if (reservations.isEmpty()) return@either emptyList()

        runningReservationListItems(
            reservations = reservations,
            eventInstanceRepository = eventInstanceRepository,
            eventSeriesRepository = eventSeriesRepository,
            now = Clock.System.now(),
        )
    }

    /**
     * Připíše rezervaci bez účtu přihlášenému uživateli se shodným kontaktním e-mailem.
     *
     * Laťkou je znalost UUID rezervace — stejná, jakou má odjakživa zobrazení detailu.
     * Přivlastnění tedy nedává nikomu přístup, který by už neměl; mění jen trvalost vazby.
     *
     * Podmínky se ověřují znovu, i když je klient dostal v `ReservationDetail.claimable` —
     * ten příznak řídí jen zobrazení tlačítka.
     */
    override suspend fun claimReservation(reservationId: Uuid): Either<ReservationError.ClaimReservation, Unit> = either {
        val callerUserId = currentCallerUserId() ?: raise(ReservationError.ReservationNotFound)
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) {
            ReservationError.ReservationNotFound
        }

        // Dvojklik na vlastní rezervaci není chyba — uživatel chtěl přesně tenhle stav.
        if (reservation.registeredUserId == callerUserId) return@either Unit
        ensure(reservation.registeredUserId == null) { ReservationError.AlreadyClaimed }

        val callerEmail = userRepository.findById(callerUserId)?.email
        ensure(reservation.isClaimableBy(callerUserId, callerEmail)) {
            // Rozlišit, proč to neprošlo: neshodu e-mailu má uživatel šanci pochopit,
            // doběhlou rezervaci taky. Obojí jsou stavy, které tlačítko vůbec neukazuje.
            if (reservation.status !in ACTIVE_SIGNUP_STATUSES) ReservationError.NotClaimable
            else ReservationError.EmailDoesNotMatch
        }

        val target: ReservationTarget = ensureNotNull(
            when (val ref = reservation.reference) {
                is Reference.Instance -> eventInstanceRepository.get(ref.id)?.let { ReservationTarget.Instance(it) }
                is Reference.Series -> eventSeriesRepository.get(ref.id)?.let { ReservationTarget.Series(it) }
            }
        ) { ReservationError.NotClaimable }
        ensure(target.isStillRunning(Clock.System.now())) { ReservationError.NotClaimable }

        // Úzký UPDATE s podmínkou `registered_user_id IS NULL` — nejen aby nepřepsal
        // souběžné spárování platby, ale i jako zámek proti dvěma claimům naráz.
        ensure(reservationRepository.linkToUser(reservationId, callerUserId)) { ReservationError.AlreadyClaimed }
        logger.info("Reservation claimed id=$reservationId user=$callerUserId")

        val subject = auditSubjectFor(target, reservationId)
        audit.record(
            type = AuditEventType.RESERVATION_CLAIMED,
            subjectLabel = reservation.contactName,
            seriesId = subject.seriesId,
            instanceId = subject.instanceId,
            reservationId = reservationId,
            detail = "shoda e-mailu ${reservation.contactEmail}",
        )
    }

}

/**
 * Rezervace na položky seznamu, s vyhozením těch, jejichž akce nebo kurz už neběží.
 *
 * Sdílí ho „Moje rezervace“ i nabídka přidání rezervací k účtu po registraci —
 * kdyby každá měla svou kopii filtru, nabídka by dřív nebo později slibovala
 * rezervaci, která se pak v seznamu neukáže.
 */
internal suspend fun runningReservationListItems(
    reservations: List<Reservation>,
    eventInstanceRepository: EventInstanceRepository,
    eventSeriesRepository: EventSeriesRepository,
    now: Instant,
): List<MyReservationListItem> {
    if (reservations.isEmpty()) return emptyList()

    val instanceIds = reservations.mapNotNull { (it.reference as? Reference.Instance)?.id }
    val seriesIds = reservations.mapNotNull { (it.reference as? Reference.Series)?.id }
    val events = eventInstanceRepository.getAll(instanceIds).associateBy { it.id }
    val series = eventSeriesRepository.getAll(seriesIds).associateBy { it.id }

    return reservations.mapNotNull { reservation ->
        when (val ref = reservation.reference) {
            is Reference.Instance -> {
                val event = events[ref.id] ?: return@mapNotNull null
                if (!ReservationTarget.Instance(event).isStillRunning(now)) return@mapNotNull null
                reservation.toListItem(
                    title = event.title,
                    startDateTime = event.startDateTime,
                    isSeries = false,
                )
            }
            is Reference.Series -> {
                val seriesItem = series[ref.id] ?: return@mapNotNull null
                if (!ReservationTarget.Series(seriesItem).isStillRunning(now)) return@mapNotNull null
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