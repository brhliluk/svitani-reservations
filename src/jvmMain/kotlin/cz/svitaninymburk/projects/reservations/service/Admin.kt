package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.admin.AdminDashboardData
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.admin.AdminParticipantRow
import cz.svitaninymburk.projects.reservations.admin.AdminPendingReservation
import cz.svitaninymburk.projects.reservations.admin.AdminReservationListItem
import cz.svitaninymburk.projects.reservations.admin.AdminUpcomingEvent
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AdminDashboardService(
    private val eventDefinitionRepository: EventDefinitionRepository,
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

        val success = reservationRepository.updateStatus(reservationId, Reservation.Status.CONFIRMED)

        if (!success) {
            raise(AdminError.FailedToMarkReservationPaid("Chyba při aktualizaci stavu v databázi."))
        }

        // 💡 TIP DO BUDOUCNA: Tady můžeš zavolat emailService a poslat mamince "Platba přijata, těšíme se na vás!"
    }

    override suspend fun getEventDetail(eventId: Uuid, isSeries: Boolean): Either<AdminError.GetEventDetail, AdminEventDetailData> = either {
        val title: String
        val subtitle: String
        val capacity: Int
        val occupiedSpots: Int

        if (isSeries) {
            val series = ensureNotNull(eventSeriesRepository.get(eventId)) { AdminError.EventSeriesNotFound(eventId) }
            title = series.title
            subtitle = "Kurz (${series.lessonCount} lekcí) • Od ${series.startDate}"
            capacity = series.capacity
            occupiedSpots = series.occupiedSpots
        } else {
            val instance = ensureNotNull(eventInstanceRepository.get(eventId)) { AdminError.EventInstanceNotFound(eventId) }
            title = instance.title
            val time = "${instance.startDateTime.date.day}.${instance.startDateTime.date.month.number}. ${instance.startDateTime.hour}:${instance.startDateTime.minute.toString().padStart(2, '0')}"
            subtitle = "Jednorázová událost • $time"
            capacity = instance.capacity
            occupiedSpots = instance.occupiedSpots
        }

        val reference = if (isSeries) Reference.Series(eventId) else Reference.Instance(eventId)

        val eventReservations = reservationRepository.findByReference(reference)

        val activeReservations = eventReservations.filter { it.status != Reservation.Status.CANCELLED }
        val totalCollected = activeReservations
            .filter { it.status == Reservation.Status.CONFIRMED }
            .sumOf { it.totalPrice }

        val participants = activeReservations
            .sortedBy { it.createdAt }
            .map { res ->
                AdminParticipantRow(
                    reservationId = res.id,
                    contactName = res.contactName,
                    contactEmail = res.contactEmail,
                    contactPhone = res.contactPhone,
                    seatCount = res.seatCount,
                    totalPrice = res.totalPrice,
                    status = res.status,
                    paymentType = res.paymentType
                )
            }

        AdminEventDetailData(
            eventId = eventId,
            title = title,
            subtitle = subtitle,
            capacity = capacity,
            occupiedSpots = occupiedSpots,
            totalCollected = totalCollected,
            participants = participants
        )
    }

    override suspend fun getAllReservations(searchQuery: String?): Either<AdminError.GetReservations, List<AdminReservationListItem>> = either {
        try {
            var reservations = reservationRepository.findAll()

            if (!searchQuery.isNullOrBlank()) {
                val query = searchQuery.lowercase()
                reservations = reservations.filter {
                    it.contactName.lowercase().contains(query) ||
                            it.contactEmail.lowercase().contains(query) ||
                            it.variableSymbol?.lowercase()?.contains(query) == true
                }
            }

            val sortedReservations = reservations.sortedByDescending { it.createdAt }

            sortedReservations.map { res ->
                var eventTitle = "Neznámá událost"
                var eventDate = ""

                when (val ref = res.reference) {
                    is Reference.Instance -> {
                        val instance = eventInstanceRepository.get(ref.id)
                        if (instance != null) {
                            eventTitle = instance.title
                            eventDate = "${instance.startDateTime.date.day}.${instance.startDateTime.date.month.number}. ${instance.startDateTime.hour}:${instance.startDateTime.minute.toString().padStart(2, '0')}"
                        }
                    }
                    is Reference.Series -> {
                        val series = eventSeriesRepository.get(ref.id)
                        if (series != null) {
                            eventTitle = series.title
                            eventDate = "Kurz (od ${series.startDate})"
                        }
                    }
                }

                AdminReservationListItem(
                    id = res.id,
                    contactName = res.contactName,
                    contactEmail = res.contactEmail,
                    eventTitle = eventTitle,
                    eventDate = eventDate,
                    seatCount = res.seatCount,
                    totalPrice = res.totalPrice,
                    variableSymbol = res.variableSymbol,
                    status = res.status,
                    paymentType = res.paymentType,
                    createdAt = res.createdAt
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.FailedToGetReservations("Nepodařilo se načíst seznam rezervací: ${e.message}"))
        }
    }

    override suspend fun getAllEvents(): Either<AdminError.GetEvents, List<AdminEventListItem>> = either {
        try {
            val series = eventSeriesRepository.getAll(null)
            val seriesDtos = series.map { s ->
                AdminEventListItem(
                    id = s.id,
                    title = s.title,
                    isSeries = true,
                    dateInfo = "Od ${s.startDate} (${s.lessonCount} lekcí)",
                    capacity = s.capacity,
                    occupiedSpots = s.occupiedSpots,
                    priceString = "${s.price} Kč",
                )
            }

            val allInstances = eventInstanceRepository.getAll(null)
            val instances = allInstances.filter { it.seriesId == null }
            val instanceDtos = instances.map { i ->
                val time = "${i.startDateTime.date.day}.${i.startDateTime.date.month.number}. ${i.startDateTime.hour}:${i.startDateTime.minute.toString().padStart(2, '0')}"
                AdminEventListItem(
                    id = i.id,
                    title = i.title,
                    isSeries = false,
                    dateInfo = time,
                    capacity = i.capacity,
                    occupiedSpots = i.occupiedSpots,
                    priceString = "${i.price} Kč"
                )
            }

            val usedDefinitionIds = (allInstances.map { it.definitionId } + series.map { it.definitionId }).toSet()
            val definitions = eventDefinitionRepository.getAll(null)
            val definitionDtos = definitions
                .filter { it.id !in usedDefinitionIds }
                .map { d ->
                    AdminEventListItem(
                        id = d.id,
                        title = d.title,
                        isSeries = false,
                        dateInfo = "Šablona – bez termínů",
                        capacity = d.defaultCapacity,
                        occupiedSpots = 0,
                        priceString = "${d.defaultPrice} Kč",
                        isDefinitionOnly = true,
                    )
                }

            (seriesDtos + instanceDtos + definitionDtos).sortedBy { it.title }

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
                recurrenceType = request.recurrenceType,
                recurrenceEndDate = request.recurrenceEndDate,
                customFields = request.customFields
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
                startDate = request.startDate,
                endDate = request.endDate,
                lessonCount = request.lessonCount,
                allowedPaymentTypes = request.allowedPaymentTypes,
                customFields = request.customFields,
            )

            eventSeriesRepository.create(newSeries)

            newSeries.id
        } catch (e: Exception) {
            e.printStackTrace()
            raise(AdminError.FailedToCreateSeries("Nepodařilo se vytvořit kurz: ${e.message}"))
        }
    }
}
