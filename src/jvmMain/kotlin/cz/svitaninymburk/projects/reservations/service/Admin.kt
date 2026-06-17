package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.NewPaymentEvent
import cz.svitaninymburk.projects.reservations.repository.payment.PaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.UserRepository
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.admin.ReservationsPage
import cz.svitaninymburk.projects.reservations.admin.EventsPage
import cz.svitaninymburk.projects.reservations.admin.SeriesInstancesPage
import cz.svitaninymburk.projects.reservations.admin.PaymentEventsPage
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.admin.AdminDashboardData
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.admin.AdminParticipantRow
import cz.svitaninymburk.projects.reservations.admin.AdminPendingReservation
import cz.svitaninymburk.projects.reservations.admin.AdminReservationListItem
import cz.svitaninymburk.projects.reservations.admin.AdminUpcomingEvent
import cz.svitaninymburk.projects.reservations.admin.AdminUserListItem
import cz.svitaninymburk.projects.reservations.event.CreateEventAndInstancesRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventAndSeriesRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletTransaction
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import cz.svitaninymburk.projects.reservations.wallet.WalletsPage
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.util.captureEmailError
import cz.svitaninymburk.projects.reservations.util.humanReadable
import io.ktor.util.logging.KtorSimpleLogger
import arrow.fx.coroutines.parZip
import kotlin.reflect.jvm.jvmName
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AdminDashboardService(
    private val eventDefinitionRepository: EventDefinitionRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val reservationRepository: ReservationRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val paymentEventRepository: PaymentEventRepository,
    private val walletService: WalletService,
    private val refundService: RefundService,
    private val seriesLessonOptOutRepository: SeriesLessonOptOutRepository,
): AdminServiceInterface {

    private val logger = KtorSimpleLogger(this::class.jvmName)

    /** Resolve the wallet to refund into: existing for registered users, a fresh one for anonymous. */
    private suspend fun resolveWalletForRefund(reservation: Reservation): Wallet {
        val registeredUserId = reservation.registeredUserId
        return if (registeredUserId != null) {
            walletService.findOrCreateForRegisteredUser(registeredUserId, reservation.contactEmail)
        } else {
            // code = null always creates a fresh wallet (always Right); the code is emailed to the user.
            walletService.resolveAnonymousWallet(code = null, contactEmail = reservation.contactEmail, force = true)
                .getOrNull() ?: error("resolveAnonymousWallet(null) must create a wallet")
        }
    }

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
            .filter { it.status != Reservation.Status.CANCELLED && it.status != Reservation.Status.WAITLISTED }
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

        reservationRepository.save(
            reservation.copy(status = Reservation.Status.CONFIRMED, paidAmount = reservation.totalPrice)
        )

        runCatching {
            paymentEventRepository.insert(
                NewPaymentEvent(
                    reservationId = reservationId,
                    amount = reservation.totalPrice,
                    type = reservation.paymentType,
                    source = PaymentEvent.Source.MANUAL_ADMIN,
                )
            )
        }.onFailure { e ->
            println("WARNING: Failed to record payment event for reservation $reservationId: ${e.message}")
        }

        emailService.sendPaymentReceivedConfirmation(reservation)
            .onLeft { captureEmailError(logger, "Failed to send payment-received email for reservation ${reservation.id}: $it") }
    }

    override suspend fun getEventDetail(eventId: Uuid, isSeries: Boolean): Either<AdminError.GetEventDetail, AdminEventDetailData> = either {
        val title: String
        val subtitle: String
        val capacity: Int
        val occupiedSpots: Int
        val waitlistCapacity: Int
        val customFields: List<CustomFieldDefinition>
        var isCancelled = false

        if (isSeries) {
            val series = ensureNotNull(eventSeriesRepository.get(eventId)) { AdminError.EventSeriesNotFound(eventId) }
            title = series.title
            subtitle = "Kurz (${series.lessonCount} lekcí) • Od ${series.startDate}"
            capacity = series.capacity
            occupiedSpots = series.occupiedSpots
            waitlistCapacity = series.waitlistCapacity
            customFields = series.customFields
            isCancelled = series.isCancelled
        } else {
            val instance = ensureNotNull(eventInstanceRepository.get(eventId)) { AdminError.EventInstanceNotFound(eventId) }
            title = instance.title
            subtitle = "Jednorázová událost • ${instance.startDateTime.humanReadable}"
            capacity = instance.capacity
            occupiedSpots = instance.occupiedSpots
            waitlistCapacity = instance.waitlistCapacity
            customFields = instance.customFields
            isCancelled = instance.isCancelled
        }

        val reference = if (isSeries) Reference.Series(eventId) else Reference.Instance(eventId)

        val eventReservations = reservationRepository.findByReference(reference)

        val activeReservations = eventReservations.filter { it.status != Reservation.Status.CANCELLED }
        val totalCollected = activeReservations
            .filter { it.status == Reservation.Status.CONFIRMED }
            .sumOf { it.totalPrice }

        fun toRow(res: cz.svitaninymburk.projects.reservations.reservation.Reservation) = AdminParticipantRow(
            reservationId = res.id,
            contactName = res.contactName,
            contactEmail = res.contactEmail,
            contactPhone = res.contactPhone,
            seatCount = res.seatCount,
            totalPrice = res.totalPrice,
            status = res.status,
            paymentType = res.paymentType,
            createdAt = res.createdAt,
            customValues = res.customValues,
        )

        val participants = activeReservations
            .filter { it.status != Reservation.Status.WAITLISTED }
            .sortedBy { it.createdAt }
            .map { toRow(it) }

        val waitlist = activeReservations
            .filter { it.status == Reservation.Status.WAITLISTED }
            .sortedBy { it.createdAt }
            .map { toRow(it) }

        AdminEventDetailData(
            eventId = eventId,
            title = title,
            subtitle = subtitle,
            capacity = capacity,
            occupiedSpots = occupiedSpots,
            totalCollected = totalCollected,
            customFields = customFields,
            participants = participants,
            waitlist = waitlist,
            waitlistCapacity = waitlistCapacity,
            isCancelled = isCancelled,
        )
    }

    override suspend fun getAllReservations(searchQuery: String?, page: Int, pageSize: Int, includeCancelled: Boolean): Either<AdminError.GetReservations, ReservationsPage> = either {
        ensure(page >= 0) { AdminError.FailedToGetReservations("Neplatná stránka.") }
        ensure(pageSize in 1..200) { AdminError.FailedToGetReservations("Neplatná velikost stránky.") }
        try {
            val (reservations, totalCount) = parZip(
                { reservationRepository.findAllPaged(searchQuery, page, pageSize, includeCancelled) },
                { reservationRepository.countAll(searchQuery, includeCancelled) },
            ) { r, c -> r to c }

            val instanceIds = reservations.mapNotNull { (it.reference as? Reference.Instance)?.id }
            val seriesIds = reservations.mapNotNull { (it.reference as? Reference.Series)?.id }
            val (instancesById, seriesById) = parZip(
                { eventInstanceRepository.getAll(instanceIds).associateBy { it.id } },
                { eventSeriesRepository.getAll(seriesIds).associateBy { it.id } },
            ) { i, s -> i to s }

            val items = reservations.map { res ->
                var eventTitle = "Neznámá událost"
                var eventDate = ""
                var customFields: List<CustomFieldDefinition> = emptyList()

                when (val ref = res.reference) {
                    is Reference.Instance -> instancesById[ref.id]?.let { instance ->
                        eventTitle = instance.title
                        eventDate = instance.startDateTime.humanReadable
                        customFields = instance.customFields
                    }
                    is Reference.Series -> seriesById[ref.id]?.let { series ->
                        eventTitle = series.title
                        eventDate = "Kurz (od ${series.startDate})"
                        customFields = series.customFields
                    }
                }

                AdminReservationListItem(
                    id = res.id,
                    contactName = res.contactName,
                    contactEmail = res.contactEmail,
                    contactPhone = res.contactPhone,
                    eventTitle = eventTitle,
                    eventDate = eventDate,
                    seatCount = res.seatCount,
                    totalPrice = res.totalPrice,
                    variableSymbol = res.variableSymbol,
                    status = res.status,
                    paymentType = res.paymentType,
                    walletDeductedAmount = res.walletDeductedAmount,
                    createdAt = res.createdAt,
                    customFields = customFields,
                    customValues = res.customValues,
                )
            }

            ReservationsPage(items = items, page = page, pageSize = pageSize, totalCount = totalCount)
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.FailedToGetReservations("Nepodařilo se načíst seznam rezervací: ${e.message}"))
        }
    }

    override suspend fun getAllEvents(page: Int, pageSize: Int, includePast: Boolean): Either<AdminError.GetEvents, EventsPage> = either {
        ensure(page >= 0) { AdminError.FailedToGetEvents("Neplatná stránka.") }
        ensure(pageSize in 1..200) { AdminError.FailedToGetEvents("Neplatná velikost stránky.") }
        try {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val today = now.date

            fun isInstancePast(i: EventInstance) = i.endDateTime < now
            fun isSeriesPast(s: EventSeries) = s.endDate < today

            // Načteme všechny děti, abychom zjistili, které definice mají jen proběhlé termíny.
            val (allInstances, allSeries) = parZip(
                { eventInstanceRepository.getAll(null) },
                { eventSeriesRepository.getAll(null) },
            ) { i, s -> i to s }

            val excludeDefinitionIds: Set<Uuid> = if (includePast) {
                emptySet()
            } else {
                val pastDefIds = (allInstances.filter { isInstancePast(it) }.map { it.definitionId } +
                    allSeries.filter { isSeriesPast(it) }.map { it.definitionId }).toSet()
                val upcomingDefIds = (allInstances.filter { !isInstancePast(it) }.map { it.definitionId } +
                    allSeries.filter { !isSeriesPast(it) }.map { it.definitionId }).toSet()
                // Definice, které mají děti, ale žádnou nadcházející. Definice bez dětí zůstávají.
                pastDefIds - upcomingDefIds
            }

            val (totalDefinitionCount, definitions) = parZip(
                { eventDefinitionRepository.countAll(excludeDefinitionIds) },
                { eventDefinitionRepository.findAllPaged(page, pageSize, excludeDefinitionIds) },
            ) { count, defs -> count to defs }
            val definitionIds = definitions.map { it.id }.toSet()

            val seriesDtos = allSeries
                .filter { it.definitionId in definitionIds && (includePast || !isSeriesPast(it)) }
                .map { s ->
                    AdminEventListItem(
                        id = s.id,
                        definitionId = s.definitionId,
                        title = s.title,
                        isSeries = true,
                        dateInfo = "Od ${s.startDate.humanReadable} (${s.lessonCount} lekcí)",
                        capacity = s.capacity,
                        occupiedSpots = s.occupiedSpots,
                        priceString = "${s.price} Kč",
                        isPublished = s.isPublished,
                        isCancelled = s.isCancelled,
                        isPast = isSeriesPast(s),
                    )
                }

            val instanceDtos = allInstances
                .filter { it.seriesId == null && it.definitionId in definitionIds && (includePast || !isInstancePast(it)) }
                .map { i ->
                    AdminEventListItem(
                        id = i.id,
                        definitionId = i.definitionId,
                        title = i.title,
                        isSeries = false,
                        dateInfo = i.startDateTime.humanReadable,
                        capacity = i.capacity,
                        occupiedSpots = i.occupiedSpots,
                        priceString = "${i.price} Kč",
                        isPublished = i.isPublished,
                        isCancelled = i.isCancelled,
                        isPast = isInstancePast(i),
                    )
                }

            val definitionDtos = definitions.map { d ->
                AdminEventListItem(
                    id = d.id,
                    definitionId = null,
                    title = d.title,
                    isSeries = false,
                    dateInfo = "Šablona",
                    capacity = d.defaultCapacity,
                    occupiedSpots = 0,
                    priceString = "${d.defaultPrice} Kč",
                    isDefinitionOnly = true,
                )
            }

            EventsPage(
                items = seriesDtos + instanceDtos + definitionDtos,
                page = page,
                pageSize = pageSize,
                totalDefinitionCount = totalDefinitionCount,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.FailedToGetEvents("Nepodařilo se načíst katalog událostí: ${e.message}"))
        }
    }

    override suspend fun createEventDefinition(request: CreateEventDefinitionRequest): Either<AdminError.CreateEvent, Uuid> = either {
        try {
            val newDefinition = EventDefinition(
                id = Uuid.random(),
                title = request.title,
                description = request.description,
                defaultPrice = request.defaultPrice,
                defaultCapacity = request.defaultCapacity,
                defaultDuration = request.defaultDuration,
                allowedPaymentTypes = request.allowedPaymentTypes,
                customFields = request.customFields,
                ownerEmails = request.ownerEmails,
                showAttendeeCount = request.showAttendeeCount,
            )

            eventDefinitionRepository.create(newDefinition)

            newDefinition.id
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.FailedToCreateEvent("Nepodařilo se vytvořit definici: ${e.message}"))
        }
    }

    override suspend fun createEventSeries(request: CreateEventSeriesRequest): Either<AdminError.CreateSeries, Uuid> = either {
        try {
            val newSeries = EventSeries(
                id = Uuid.random(),
                definitionId = request.definitionId,
                title = request.title,
                description = request.description,
                price = request.price,
                capacity = request.capacity,
                waitlistCapacity = request.waitlistCapacity,
                startDate = request.startDate,
                endDate = request.endDate,
                lessonCount = request.lessonCount,
                allowedPaymentTypes = request.allowedPaymentTypes,
                customFields = request.customFields,
                lessonDayOfWeek = request.lessonDayOfWeek,
                lessonStartTime = request.lessonStartTime,
                lessonEndTime = request.lessonEndTime,
                ownerEmails = request.ownerEmails,
                showAttendeeCount = request.showAttendeeCount,
                lessonRefundAmount = request.lessonRefundAmount,
                reservationDeadline = request.reservationDeadline,
                reservationDeadlineMessage = request.reservationDeadlineMessage,
                isPublished = request.isPublished,
            )

            eventSeriesRepository.create(newSeries)

            // Auto-generate lesson instances if schedule is defined
            val customLessons = request.customLessons
            if (customLessons != null) {
                customLessons.forEach { lesson ->
                    eventInstanceRepository.create(
                        EventInstance(
                            id = Uuid.random(),
                            definitionId = newSeries.definitionId,
                            seriesId = newSeries.id,
                            title = newSeries.title,
                            description = newSeries.description,
                            startDateTime = lesson.startDateTime,
                            endDateTime = lesson.endDateTime,
                            price = newSeries.price,
                            capacity = newSeries.capacity,
                            allowedPaymentTypes = newSeries.allowedPaymentTypes,
                            customFields = newSeries.customFields,
                            isDropIn = lesson.isDropIn,
                            ownerEmails = newSeries.ownerEmails,
                            showAttendeeCount = newSeries.showAttendeeCount,
                            isPublished = request.isPublished,
                        )
                    )
                }
            } else if (newSeries.lessonDayOfWeek != null && newSeries.lessonStartTime != null && newSeries.lessonEndTime != null) {
                val lessonStartTime = newSeries.lessonStartTime!!
                val lessonEndTime = newSeries.lessonEndTime!!
                var date = newSeries.startDate
                while (date.dayOfWeek != newSeries.lessonDayOfWeek) {
                    date = date.plus(1, DateTimeUnit.DAY)
                }
                repeat(newSeries.lessonCount) {
                    eventInstanceRepository.create(
                        EventInstance(
                            id = Uuid.random(),
                            definitionId = newSeries.definitionId,
                            seriesId = newSeries.id,
                            title = newSeries.title,
                            description = newSeries.description,
                            startDateTime = LocalDateTime(date, lessonStartTime),
                            endDateTime = LocalDateTime(date, lessonEndTime),
                            price = newSeries.price,
                            capacity = newSeries.capacity,
                            allowedPaymentTypes = newSeries.allowedPaymentTypes,
                            customFields = newSeries.customFields,
                            isDropIn = false,
                            ownerEmails = newSeries.ownerEmails,
                            showAttendeeCount = newSeries.showAttendeeCount,
                            isPublished = request.isPublished,
                        )
                    )
                    date = date.plus(1, DateTimeUnit.WEEK)
                }
            }

            newSeries.id
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.FailedToCreateSeries("Nepodařilo se vytvořit kurz: ${e.message}"))
        }
    }

    override suspend fun createEventAndInstances(request: CreateEventAndInstancesRequest): Either<AdminError.CreateEvent, Uuid> = either {
        try {
            val newDefinition = EventDefinition(
                id = Uuid.random(),
                title = request.title,
                description = request.description,
                defaultPrice = request.defaultPrice,
                defaultCapacity = request.defaultCapacity,
                defaultDuration = request.defaultDuration,
                allowedPaymentTypes = request.allowedPaymentTypes,
                customFields = request.customFields,
                ownerEmails = request.ownerEmails,
                showAttendeeCount = request.showAttendeeCount,
            )
            eventDefinitionRepository.create(newDefinition)

            val tz = TimeZone.currentSystemDefault()
            request.dateTimes.forEach { startDateTime ->
                eventInstanceRepository.create(
                    EventInstance(
                        id = Uuid.random(),
                        definitionId = newDefinition.id,
                        title = newDefinition.title,
                        description = newDefinition.description,
                        startDateTime = startDateTime,
                        endDateTime = (startDateTime.toInstant(tz) + newDefinition.defaultDuration).toLocalDateTime(tz),
                        price = newDefinition.defaultPrice,
                        capacity = newDefinition.defaultCapacity,
                        waitlistCapacity = request.defaultWaitlistCapacity,
                        allowedPaymentTypes = newDefinition.allowedPaymentTypes,
                        customFields = newDefinition.customFields,
                        ownerEmails = newDefinition.ownerEmails,
                        showAttendeeCount = newDefinition.showAttendeeCount,
                        reservationDeadline = request.reservationDeadline,
                        reservationDeadlineMessage = request.reservationDeadlineMessage,
                        isPublished = request.isPublished,
                    )
                )
            }
            newDefinition.id
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.FailedToCreateEvent("Nepodařilo se vytvořit událost: ${e.message}"))
        }
    }

    override suspend fun createEventAndSeries(request: CreateEventAndSeriesRequest): Either<AdminError.CreateSeries, Uuid> = either {
        try {
            val newDefinition = EventDefinition(
                id = Uuid.random(),
                title = request.title,
                description = request.description,
                defaultPrice = request.defaultPrice,
                defaultCapacity = request.defaultCapacity,
                defaultDuration = request.defaultDuration,
                allowedPaymentTypes = request.allowedPaymentTypes,
                customFields = request.customFields,
                ownerEmails = request.ownerEmails,
                showAttendeeCount = request.showAttendeeCount,
            )
            eventDefinitionRepository.create(newDefinition)

            val newSeries = EventSeries(
                id = Uuid.random(),
                definitionId = newDefinition.id,
                title = newDefinition.title,
                description = newDefinition.description,
                price = newDefinition.defaultPrice,
                capacity = newDefinition.defaultCapacity,
                startDate = request.startDate,
                endDate = request.endDate,
                lessonCount = request.lessonCount,
                allowedPaymentTypes = newDefinition.allowedPaymentTypes,
                customFields = newDefinition.customFields,
                ownerEmails = request.ownerEmails,
                showAttendeeCount = newDefinition.showAttendeeCount,
                reservationDeadline = request.reservationDeadline,
                reservationDeadlineMessage = request.reservationDeadlineMessage,
                isPublished = request.isPublished,
            )
            eventSeriesRepository.create(newSeries)

            // Auto-generate lesson instances if schedule is defined
            val customLessons = request.customLessons
            if (customLessons != null) {
                customLessons.forEach { lesson ->
                    eventInstanceRepository.create(
                        EventInstance(
                            id = Uuid.random(),
                            definitionId = newDefinition.id,
                            seriesId = newSeries.id,
                            title = newDefinition.title,
                            description = newDefinition.description,
                            startDateTime = lesson.startDateTime,
                            endDateTime = lesson.endDateTime,
                            price = newDefinition.defaultPrice,
                            capacity = newDefinition.defaultCapacity,
                            allowedPaymentTypes = newDefinition.allowedPaymentTypes,
                            customFields = newDefinition.customFields,
                            isDropIn = lesson.isDropIn,
                            ownerEmails = newSeries.ownerEmails,
                            showAttendeeCount = newSeries.showAttendeeCount,
                            isPublished = request.isPublished,
                        )
                    )
                }
            } else if (newSeries.lessonDayOfWeek != null && newSeries.lessonStartTime != null && newSeries.lessonEndTime != null) {
                val lessonStartTime = newSeries.lessonStartTime!!
                val lessonEndTime = newSeries.lessonEndTime!!
                var date = newSeries.startDate
                while (date.dayOfWeek != newSeries.lessonDayOfWeek) {
                    date = date.plus(1, DateTimeUnit.DAY)
                }
                repeat(newSeries.lessonCount) {
                    eventInstanceRepository.create(
                        EventInstance(
                            id = Uuid.random(),
                            definitionId = newSeries.definitionId,
                            seriesId = newSeries.id,
                            title = newSeries.title,
                            description = newSeries.description,
                            startDateTime = LocalDateTime(date, lessonStartTime),
                            endDateTime = LocalDateTime(date, lessonEndTime),
                            price = newSeries.price,
                            capacity = newSeries.capacity,
                            allowedPaymentTypes = newSeries.allowedPaymentTypes,
                            customFields = newSeries.customFields,
                            isDropIn = false,
                            ownerEmails = newSeries.ownerEmails,
                            showAttendeeCount = newSeries.showAttendeeCount,
                            isPublished = request.isPublished,
                        )
                    )
                    date = date.plus(1, DateTimeUnit.WEEK)
                }
            }

            newDefinition.id
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.FailedToCreateSeries("Nepodařilo se vytvořit kurz: ${e.message}"))
        }
    }

    override suspend fun getAllUsers(): Either<AdminError.GetUsers, List<AdminUserListItem>> = either {
        try {
            val users = userRepository.findAll()
            val allReservations = reservationRepository.findAll()

            val reservationCountByUser = allReservations
                .filter { it.registeredUserId != null }
                .groupBy { it.registeredUserId!! }
                .mapValues { (_, reservations) -> reservations.size }

            users.map { user ->
                AdminUserListItem(
                    id = user.id,
                    name = user.name,
                    surname = user.surname,
                    email = user.email,
                    role = user.role,
                    authType = when (user) {
                        is User.Google -> AdminUserListItem.AuthType.GOOGLE
                        is User.Email -> AdminUserListItem.AuthType.EMAIL
                    },
                    reservationCount = reservationCountByUser[user.id] ?: 0
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.FailedToGetUsers("Nepodařilo se načíst seznam uživatelů: ${e.message}"))
        }
    }

    override suspend fun updateUserRole(userId: Uuid, newRole: User.Role): Either<AdminError.UpdateUserRole, Unit> = either {
        val user = ensureNotNull(userRepository.findById(userId)) { AdminError.UserNotFound(userId) }
        val updatedUser = when (user) {
            is User.Email -> user.copy(role = newRole)
            is User.Google -> user.copy(role = newRole)
        }
        userRepository.update(userId, updatedUser)
    }

    override suspend fun deleteUser(userId: Uuid): Either<AdminError.DeleteUser, Unit> = either {
        val success = userRepository.delete(userId)
        if (!success) raise(AdminError.UserNotFound(userId))
    }

    override suspend fun getEventDefinitionForEdit(id: Uuid): Either<AdminError.GetEditData, EventDefinition> = either {
        ensureNotNull(eventDefinitionRepository.get(id)) { AdminError.DefinitionNotFound(id) }
    }

    override suspend fun getEventInstanceForEdit(id: Uuid): Either<AdminError.GetEditData, EventInstance> = either {
        ensureNotNull(eventInstanceRepository.get(id)) { AdminError.InstanceNotFoundForEdit(id) }
    }

    override suspend fun getEventSeriesForEdit(id: Uuid): Either<AdminError.GetEditData, EventSeries> = either {
        ensureNotNull(eventSeriesRepository.get(id)) { AdminError.SeriesNotFoundForEdit(id) }
    }

    override suspend fun updateEventInstance(id: Uuid, request: cz.svitaninymburk.projects.reservations.event.UpdateEventInstanceRequest): Either<AdminError.UpdateEvent, Unit> = either {
        val existing = ensureNotNull(eventInstanceRepository.get(id)) { AdminError.InstanceNotFoundForEdit(id) }
        val previousStartDateTime = existing.startDateTime
        eventInstanceRepository.update(
            existing.copy(
                title = request.title,
                description = request.description,
                startDateTime = request.startDateTime,
                endDateTime = request.endDateTime,
                price = request.price,
                capacity = request.capacity,
                waitlistCapacity = request.waitlistCapacity,
                allowedPaymentTypes = request.allowedPaymentTypes,
                customFields = request.customFields,
                ownerEmails = request.ownerEmails,
                showAttendeeCount = request.showAttendeeCount,
                reservationDeadline = request.reservationDeadline,
                reservationDeadlineMessage = request.reservationDeadlineMessage,
            )
        )

        // Send reschedule notification if datetime changed and instance belongs to a series
        if (previousStartDateTime != request.startDateTime && existing.seriesId != null) {
            val seriesId = existing.seriesId!!
            val series = eventSeriesRepository.get(seriesId)
            reservationRepository.findByReference(Reference.Series(seriesId))
                .filter { it.status != Reservation.Status.CANCELLED }
                .forEach { res ->
                    emailService.sendLessonRescheduledNotification(
                        toEmail = res.contactEmail,
                        contactName = res.contactName,
                        seriesTitle = series?.title ?: existing.title,
                        oldDateTime = existing.startDateTime,
                        newDateTime = request.startDateTime,
                        locale = res.locale,
                    ).onLeft { captureEmailError(logger, "Failed to send reschedule email for ${res.id}: $it") }
                }
        }
    }

    override suspend fun setInstancePublished(id: Uuid, published: Boolean): Either<AdminError.UpdateEvent, Unit> = either {
        val existing = ensureNotNull(eventInstanceRepository.get(id)) { AdminError.InstanceNotFoundForEdit(id) }
        eventInstanceRepository.update(existing.copy(isPublished = published))
    }

    override suspend fun setSeriesPublished(id: Uuid, published: Boolean): Either<AdminError.UpdateSeries, Unit> = either {
        val existing = ensureNotNull(eventSeriesRepository.get(id)) { AdminError.SeriesNotFoundForEdit(id) }
        eventSeriesRepository.update(existing.copy(isPublished = published))
    }

    override suspend fun updateEventSeries(id: Uuid, request: cz.svitaninymburk.projects.reservations.event.UpdateEventSeriesRequest): Either<AdminError.UpdateSeries, Unit> = either {
        val existing = ensureNotNull(eventSeriesRepository.get(id)) { AdminError.SeriesNotFoundForEdit(id) }
        eventSeriesRepository.update(
            existing.copy(
                title = request.title,
                description = request.description,
                price = request.price,
                capacity = request.capacity,
                waitlistCapacity = request.waitlistCapacity,
                startDate = request.startDate,
                endDate = request.endDate,
                lessonCount = request.lessonCount,
                allowedPaymentTypes = request.allowedPaymentTypes,
                customFields = request.customFields,
                lessonDayOfWeek = request.lessonDayOfWeek,
                lessonStartTime = request.lessonStartTime,
                lessonEndTime = request.lessonEndTime,
                ownerEmails = request.ownerEmails,
                showAttendeeCount = request.showAttendeeCount,
                lessonRefundAmount = request.lessonRefundAmount,
                reservationDeadline = request.reservationDeadline,
                reservationDeadlineMessage = request.reservationDeadlineMessage,
            )
        )
    }

    override suspend fun updateEventDefinition(id: Uuid, request: cz.svitaninymburk.projects.reservations.event.UpdateEventDefinitionRequest): Either<AdminError.UpdateDefinition, Unit> = either {
        val existing = ensureNotNull(eventDefinitionRepository.get(id)) { AdminError.DefinitionNotFound(id) }
        val updated = existing.copy(
            title = request.title,
            description = request.description,
            defaultPrice = request.defaultPrice,
            defaultCapacity = request.defaultCapacity,
            defaultDuration = request.defaultDuration,
            allowedPaymentTypes = request.allowedPaymentTypes,
            customFields = request.customFields,
            ownerEmails = request.ownerEmails,
            showAttendeeCount = request.showAttendeeCount,
        )
        eventDefinitionRepository.update(updated)

        if (request.propagateToChildren) {
            val (childInstances, childSeries) = parZip(
                { eventInstanceRepository.getAllByDefinitionIds(listOf(id)) },
                { eventSeriesRepository.getAllByDefinitionIds(listOf(id)) },
            ) { i, s -> i to s }

            childInstances.forEach { instance ->
                eventInstanceRepository.update(
                    instance.copy(
                        title = request.title,
                        description = request.description,
                        price = request.defaultPrice,
                        capacity = request.defaultCapacity,
                        allowedPaymentTypes = request.allowedPaymentTypes,
                        customFields = request.customFields,
                        ownerEmails = request.ownerEmails,
                        showAttendeeCount = request.showAttendeeCount,
                    )
                )
            }

            childSeries.forEach { series ->
                eventSeriesRepository.update(
                    series.copy(
                        title = request.title,
                        description = request.description,
                        price = request.defaultPrice,
                        capacity = request.defaultCapacity,
                        allowedPaymentTypes = request.allowedPaymentTypes,
                        customFields = request.customFields,
                        ownerEmails = request.ownerEmails,
                        showAttendeeCount = request.showAttendeeCount,
                    )
                )
            }
        }
    }

    override suspend fun deleteEventInstance(id: Uuid, refund: Boolean): Either<AdminError.DeleteEvent, Unit> = either {
        val instance = ensureNotNull(eventInstanceRepository.get(id)) { AdminError.InstanceNotFoundForEdit(id) }

        reservationRepository.findByReference(Reference.Instance(id))
            .filter { it.status != Reservation.Status.CANCELLED }
            .forEach { res ->
                reservationRepository.updateStatus(res.id, Reservation.Status.CANCELLED)
                emailService.sendCancellationNotice(res.contactEmail, instance.title, res.id, res.locale)
                    .onLeft { captureEmailError(logger, "Failed to send cancellation email for ${res.id}: $it") }
                if (refund && res.paidAmount > 0.0) {
                    try {
                        refundService.refundWholeReservation(resolveWalletForRefund(res), res)
                    } catch (e: Exception) {
                        logger.error("Failed to refund reservation ${res.id}", e)
                    }
                }
            }

        eventInstanceRepository.delete(id)
    }

    override suspend fun deleteEventSeries(id: Uuid, refund: Boolean): Either<AdminError.DeleteSeries, Unit> = either {
        val series = ensureNotNull(eventSeriesRepository.get(id)) { AdminError.SeriesNotFoundForEdit(id) }

        reservationRepository.findByReference(Reference.Series(id))
            .filter { it.status != Reservation.Status.CANCELLED }
            .forEach { res ->
                reservationRepository.updateStatus(res.id, Reservation.Status.CANCELLED)
                emailService.sendCancellationNotice(res.contactEmail, series.title, res.id, res.locale)
                    .onLeft { captureEmailError(logger, "Failed to send cancellation email for ${res.id}: $it") }
                if (refund && res.paidAmount > 0.0) {
                    try {
                        refundService.refundWholeReservation(resolveWalletForRefund(res), res)
                    } catch (e: Exception) {
                        logger.error("Failed to refund reservation ${res.id}", e)
                    }
                }
            }

        eventSeriesRepository.delete(id)
    }

    override suspend fun deleteEventDefinition(id: Uuid): Either<AdminError.DeleteDefinition, Unit> = either {
        ensureNotNull(eventDefinitionRepository.get(id)) { AdminError.DefinitionNotFound(id) }

        val (childInstances, childSeries) = parZip(
            { eventInstanceRepository.getAllByDefinitionIds(listOf(id)) },
            { eventSeriesRepository.getAllByDefinitionIds(listOf(id)) },
        ) { i, s -> i to s }

        suspend fun cancelReservations(reference: Reference, eventTitle: String) {
            reservationRepository.findByReference(reference)
                .filter { it.status != Reservation.Status.CANCELLED }
                .forEach { res ->
                    reservationRepository.updateStatus(res.id, Reservation.Status.CANCELLED)
                    emailService.sendCancellationNotice(res.contactEmail, eventTitle, res.id, res.locale)
                        .onLeft { captureEmailError(logger, "Failed to send cancellation email for ${res.id}: $it") }
                    if (res.paidAmount > 0.0) {
                        try {
                            refundService.refundWholeReservation(resolveWalletForRefund(res), res)
                        } catch (e: Exception) {
                            logger.error("Failed to refund reservation ${res.id}", e)
                        }
                    }
                }
        }

        childInstances.forEach { instance ->
            cancelReservations(Reference.Instance(instance.id), instance.title)
            eventInstanceRepository.delete(instance.id)
        }
        childSeries.forEach { series ->
            cancelReservations(Reference.Series(series.id), series.title)
            eventSeriesRepository.delete(series.id)
        }

        eventDefinitionRepository.delete(id)
    }

    override suspend fun cancelEventInstance(id: Uuid, refund: Boolean): Either<AdminError.CancelEvent, Unit> = either {
        val instance = ensureNotNull(eventInstanceRepository.get(id)) {
            AdminError.InstanceNotFoundForCancel(id)
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        ensure(instance.startDateTime >= now) {
            AdminError.EventAlreadyPassed(id)
        }

        eventInstanceRepository.setCancelled(id)

        reservationRepository.findByReference(Reference.Instance(id))
            .filter { it.status != Reservation.Status.CANCELLED }
            .forEach { res ->
                reservationRepository.updateStatus(res.id, Reservation.Status.CANCELLED)
                emailService.sendCancellationNotice(res.contactEmail, instance.title, res.id, res.locale)
                    .onLeft { captureEmailError(logger, "Failed to send cancellation email for ${res.id}: $it") }
                if (refund && res.paidAmount > 0.0) {
                    try {
                        refundService.refundWholeReservation(resolveWalletForRefund(res), res)
                    } catch (e: Exception) {
                        logger.error("Failed to refund reservation ${res.id}", e)
                    }
                }
            }
    }

    override suspend fun cancelEventSeries(id: Uuid, refund: Boolean): Either<AdminError.CancelSeries, Unit> = either {
        val series = ensureNotNull(eventSeriesRepository.get(id)) {
            AdminError.SeriesNotFoundForCancel(id)
        }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val futureInstances = eventInstanceRepository.findBySeries(id)
            .filter { it.startDateTime.date >= today }

        ensure(futureInstances.isNotEmpty()) {
            AdminError.EventAlreadyPassed(id)
        }

        eventSeriesRepository.setCancelled(id)

        futureInstances.forEach { instance ->
            eventInstanceRepository.setCancelled(instance.id)
            reservationRepository.findByReference(Reference.Instance(instance.id))
                .filter { it.status != Reservation.Status.CANCELLED }
                .forEach { res ->
                    reservationRepository.updateStatus(res.id, Reservation.Status.CANCELLED)
                    emailService.sendCancellationNotice(res.contactEmail, series.title, res.id, res.locale)
                        .onLeft { captureEmailError(logger, "Failed to send cancellation email for ${res.id}: $it") }
                    if (refund && res.paidAmount > 0.0) {
                        try {
                            refundService.refundWholeReservation(resolveWalletForRefund(res), res)
                        } catch (e: Exception) {
                            logger.error("Failed to refund reservation ${res.id}", e)
                        }
                    }
                }
        }

        reservationRepository.findByReference(Reference.Series(id))
            .filter { it.status != Reservation.Status.CANCELLED }
            .forEach { res ->
                reservationRepository.updateStatus(res.id, Reservation.Status.CANCELLED)
                emailService.sendCancellationNotice(res.contactEmail, series.title, res.id, res.locale)
                    .onLeft { captureEmailError(logger, "Failed to send cancellation email for ${res.id}: $it") }
                if (refund && res.paidAmount > 0.0) {
                    try {
                        refundService.refundWholeReservation(resolveWalletForRefund(res), res)
                    } catch (e: Exception) {
                        logger.error("Failed to refund reservation ${res.id}", e)
                    }
                }
            }
    }

    override suspend fun getSeriesInstances(seriesId: Uuid, page: Int, pageSize: Int): Either<AdminError.GetInstances, SeriesInstancesPage> = either {
        ensure(page >= 0) { AdminError.GetInstances.Failed }
        ensure(pageSize in 1..200) { AdminError.GetInstances.Failed }
        try {
            val items = eventInstanceRepository.findBySeriesPaged(seriesId, page, pageSize)
            val totalCount = eventInstanceRepository.countBySeries(seriesId)
            SeriesInstancesPage(items = items, page = page, pageSize = pageSize, totalCount = totalCount)
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.GetInstances.Failed)
        }
    }

    override suspend fun cancelSeriesLesson(instanceId: Uuid): Either<AdminError.CancelLesson, Unit> = either {
        val instance = ensureNotNull(eventInstanceRepository.get(instanceId)) { AdminError.CancelLesson.InstanceNotFound }
        val seriesId = instance.seriesId ?: raise(AdminError.CancelLesson.InstanceNotFound)

        try {
            // Mark instance as cancelled
            eventInstanceRepository.update(instance.copy(isCancelled = true))

            // Notify all active series enrollees + refund the lesson amount
            val series = eventSeriesRepository.get(seriesId)
            val refundAmount = series?.lessonRefundAmount ?: 0.0
            reservationRepository.findByReference(Reference.Series(seriesId))
                .filter { it.status != Reservation.Status.CANCELLED }
                .forEach { res ->
                    emailService.sendLessonCancelledNotification(
                        toEmail = res.contactEmail,
                        contactName = res.contactName,
                        seriesTitle = series?.title ?: instance.title,
                        lessonDateTime = instance.startDateTime,
                        locale = res.locale,
                    ).onLeft { captureEmailError(logger, "Failed to send lesson-cancelled email for ${res.id}: $it") }

                    val alreadyOptedOut = seriesLessonOptOutRepository
                        .findByReservationAndInstance(res.id, instanceId) != null
                    if (refundAmount > 0.0 && res.paidAmount > 0.0 && !alreadyOptedOut) {
                        try {
                            refundService.refundFixedAmount(
                                resolveWalletForRefund(res), res, refundAmount,
                                WalletTransactionReason.LESSON_OPT_OUT_REFUND,
                            )
                        } catch (e: Exception) {
                            logger.error("Failed to refund reservation ${res.id}", e)
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.CancelLesson.Failed)
        }
    }

    override suspend fun getPaymentEvents(page: Int, pageSize: Int): Either<AdminError.GetPaymentEvents, PaymentEventsPage> = either {
        ensure(page >= 0) { AdminError.FailedToGetPaymentEvents("Neplatná stránka.") }
        ensure(pageSize in 1..200) { AdminError.FailedToGetPaymentEvents("Neplatná velikost stránky.") }
        val items = paymentEventRepository.findAll(page, pageSize)
        val total = paymentEventRepository.countAll()
        PaymentEventsPage(items = items, page = page, pageSize = pageSize, totalCount = total)
    }

    override suspend fun getWallets(page: Int, pageSize: Int): Either<AdminError.GetWallets, WalletsPage> =
        Either.catch { walletService.getAll(page, pageSize) }
            .mapLeft { AdminError.WalletOperationFailed }

    override suspend fun getWalletTransactions(walletId: String): Either<AdminError.GetWallets, List<WalletTransaction>> =
        Either.catch { walletService.getTransactions(Uuid.parse(walletId)) }
            .mapLeft { AdminError.WalletOperationFailed }

    override suspend fun adjustWalletBalance(walletId: String, amount: Double, note: String, isCredit: Boolean): Either<AdminError.GetWallets, Wallet> =
        Either.catch {
            val id = Uuid.parse(walletId)
            if (isCredit) walletService.adminCredit(id, amount, note)
            else walletService.adminDebit(id, amount, note)
        }.mapLeft { AdminError.WalletOperationFailed }
}
