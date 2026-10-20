package cz.svitaninymburk.projects.reservations.slot

import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.event.PriceModifier
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.repository.audit.AuditRepository
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.audit.NewAuditEvent
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.AuditService
import cz.svitaninymburk.projects.reservations.slot.repository.InMemoryAvailabilityExceptionRepository
import cz.svitaninymburk.projects.reservations.slot.repository.InMemoryAvailabilityRuleRepository
import cz.svitaninymburk.projects.reservations.slot.repository.InMemorySlotServiceRepository
import cz.svitaninymburk.projects.reservations.slot.repository.InMemorySlotStore
import cz.svitaninymburk.projects.reservations.util.APP_TIMEZONE
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Správa hlídání. „Teď“ je pondělí 19. 10. 2026 8:00; služba má okna v út a čt 8–12.
 */
class SlotAdminServiceSpec {

    private val now = LocalDateTime(2026, 10, 19, 8, 0)
    private val tuesday = LocalDate(2026, 10, 20)

    private val store = InMemorySlotStore()
    private val serviceRepo = InMemorySlotServiceRepository(store)
    private val ruleRepo = InMemoryAvailabilityRuleRepository(store)
    private val exceptionRepo = InMemoryAvailabilityExceptionRepository(store)
    private val reservationRepo = InMemoryReservationRepository()
    private val auditRepo = RecordingAudit()

    /** Co by jádro stornovalo: reference, název a vratka. */
    private val cancelled = mutableListOf<Triple<Collection<Reference>, String, Boolean>>()

    private val admin = SlotAdminService(
        services = serviceRepo,
        rules = ruleRepo,
        exceptions = exceptionRepo,
        availability = SlotAvailability(serviceRepo, ruleRepo, exceptionRepo, reservationRepo),
        reservations = reservationRepo,
        audit = AuditService(auditRepo),
        canceller = { refs, title, refund -> cancelled += Triple(refs, title, refund) },
        clock = object : Clock {
            override fun now(): Instant = this@SlotAdminServiceSpec.now.toInstant(APP_TIMEZONE)
        },
    )

    private val draft = SlotService(
        id = Uuid.random(),
        title = "  Hlídání  ",
        description = "",
        step = 30.minutes,
        minDuration = 1.hours,
        maxDuration = 4.hours,
        pricePerHour = 150.0,
        capacity = 4,
        maxSeatsPerBooking = 3,
        allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
        ownerEmails = listOf(" lektor@example.cz ", ""),
        bookingHorizon = 14.days,
    )

    private fun rule(serviceId: Uuid, day: DayOfWeek, from: Int, to: Int, validFrom: LocalDate = LocalDate(2026, 1, 1), validTo: LocalDate? = null) =
        AvailabilityRule(Uuid.random(), serviceId, day, LocalTime(from, 0), LocalTime(to, 0), validFrom, validTo)

    private fun weekRules(serviceId: Uuid) =
        listOf(rule(serviceId, DayOfWeek.TUESDAY, 8, 12), rule(serviceId, DayOfWeek.THURSDAY, 8, 12))

    private fun created(): Uuid = runBlocking {
        val id = admin.createService(draft).getOrNull()!!
        admin.saveSchedule(id, weekRules(id), emptyList()).getOrNull()!!
        id
    }

    private fun reserve(serviceId: Uuid, start: LocalDateTime, end: LocalDateTime, name: String = "Nováková", status: Reservation.Status = Reservation.Status.CONFIRMED) =
        runBlocking {
            reservationRepo.save(
                Reservation(
                    id = Uuid.random(),
                    reference = Reference.Slot(serviceId, start, end),
                    contactName = name,
                    contactEmail = "jana@example.cz",
                    seatCount = 2,
                    totalPrice = 300.0,
                    status = status,
                    createdAt = Instant.fromEpochSeconds(0),
                    customValues = emptyMap(),
                    paymentType = PaymentType.BANK_TRANSFER,
                )
            )
        }

    @Test
    fun createdServiceIsNormalizedHiddenAndAudited() = runBlocking {
        val id = admin.createService(draft).getOrNull()!!
        val stored = serviceRepo.get(id)!!
        assertTrue(id != draft.id)
        assertEquals("Hlídání", stored.title)
        assertEquals(listOf("lektor@example.cz"), stored.ownerEmails)
        assertEquals(false, stored.isPublished)
        assertEquals(listOf(AuditEventType.EVENT_CREATED), auditRepo.types)
    }

    @Test
    fun invalidServiceIsRejectedWithAllProblems() = runBlocking {
        val bad = draft.copy(title = " ", minDuration = 45.minutes, maxSeatsPerBooking = 9)
        val error = admin.createService(bad).leftOrNull()
        assertIs<SlotAdminError.InvalidService>(error)
        assertEquals(
            setOf(SlotServiceProblem.TITLE_BLANK, SlotServiceProblem.MIN_DURATION_INVALID, SlotServiceProblem.MAX_SEATS_INVALID),
            error.problems.toSet(),
        )
        assertTrue(serviceRepo.getAll().isEmpty())
    }

    @Test
    fun timeMultiplierFieldIsRejected() = runBlocking {
        val field = TimeRangeFieldDefinition(key = "cas", label = "Čas", priceModifier = PriceModifier.TimeMultiplier)
        val error = admin.createService(draft.copy(customFields = listOf(field))).leftOrNull()
        assertIs<SlotAdminError.InvalidService>(error)
        assertEquals(listOf(SlotServiceProblem.TIME_MULTIPLIER_FIELD), error.problems)
    }

    @Test
    fun updateKeepsPublicationWhichHasItsOwnAction() = runBlocking {
        val id = created()
        admin.setPublished(id, true).getOrNull()!!
        admin.updateService(serviceRepo.get(id)!!.copy(title = "Hlídání dětí", isPublished = false)).getOrNull()!!
        val stored = serviceRepo.get(id)!!
        assertEquals("Hlídání dětí", stored.title)
        assertTrue(stored.isPublished)
        assertTrue(AuditEventType.EVENT_PUBLISHED in auditRepo.types)
    }

    @Test
    fun unknownServiceIsNotFound() = runBlocking {
        assertEquals(SlotAdminError.ServiceNotFound, admin.getService(Uuid.random()).leftOrNull())
        assertEquals(SlotAdminError.ServiceNotFound, admin.deleteService(Uuid.random(), refund = true).leftOrNull())
    }

    @Test
    fun detailCountsOnlyUpcomingActiveReservations() = runBlocking {
        val id = created()
        reserve(id, LocalDateTime(2026, 10, 20, 9, 0), LocalDateTime(2026, 10, 20, 10, 0))
        reserve(id, LocalDateTime(2026, 10, 13, 9, 0), LocalDateTime(2026, 10, 13, 10, 0)) // minulý týden
        reserve(id, LocalDateTime(2026, 10, 22, 9, 0), LocalDateTime(2026, 10, 22, 10, 0), status = Reservation.Status.CANCELLED)
        val detail = admin.getService(id).getOrNull()!!
        assertEquals(1, detail.upcomingReservationCount)
        assertEquals(2, detail.rules.size)
    }

    @Test
    fun deleteCancelsActiveReservationsAndRemovesTheService() = runBlocking {
        val id = created()
        reserve(id, LocalDateTime(2026, 10, 20, 9, 0), LocalDateTime(2026, 10, 20, 10, 0))
        reserve(id, LocalDateTime(2026, 10, 22, 9, 0), LocalDateTime(2026, 10, 22, 11, 0))
        reserve(id, LocalDateTime(2026, 10, 27, 9, 0), LocalDateTime(2026, 10, 27, 10, 0), status = Reservation.Status.CANCELLED)

        admin.deleteService(id, refund = true).getOrNull()!!

        val (refs, title, refund) = cancelled.single()
        assertEquals(2, refs.size)
        assertEquals("Hlídání", title)
        assertTrue(refund)
        assertNull(serviceRepo.get(id))
        assertTrue(ruleRepo.findByService(id).isEmpty())
        assertTrue(AuditEventType.EVENT_DELETED in auditRepo.types)
    }

    @Test
    fun previewListsReservationsOutsideTheNewSchedule() = runBlocking {
        val id = created()
        reserve(id, LocalDateTime(2026, 10, 20, 9, 0), LocalDateTime(2026, 10, 20, 10, 0), name = "Úterý ráno")
        reserve(id, LocalDateTime(2026, 10, 20, 11, 0), LocalDateTime(2026, 10, 20, 12, 0), name = "Úterý v jedenáct")
        reserve(id, LocalDateTime(2026, 10, 22, 9, 0), LocalDateTime(2026, 10, 22, 10, 0), name = "Čtvrtek")
        // Proběhlá rezervace změnu rozvrhu nezajímá.
        reserve(id, LocalDateTime(2026, 10, 13, 9, 0), LocalDateTime(2026, 10, 13, 10, 0), name = "Minulost")

        // Úterý jen 8–11 a čtvrtek 22. 10. zavřeno.
        val newRules = listOf(rule(id, DayOfWeek.TUESDAY, 8, 11), rule(id, DayOfWeek.THURSDAY, 8, 12))
        val closedThursday = AvailabilityException(Uuid.random(), id, LocalDate(2026, 10, 22), closed = true)

        val affected = admin.previewSchedule(id, newRules, listOf(closedThursday)).getOrNull()!!
        assertEquals(setOf("Úterý v jedenáct", "Čtvrtek"), affected.map { it.contactName }.toSet())
    }

    @Test
    fun saveReplacesRulesAndExceptionsWithoutCancellingAnything() = runBlocking {
        val id = created()
        reserve(id, LocalDateTime(2026, 10, 22, 9, 0), LocalDateTime(2026, 10, 22, 10, 0))
        val old = AvailabilityException(Uuid.random(), id, LocalDate(2026, 10, 27), closed = true)
        admin.saveSchedule(id, weekRules(id), listOf(old)).getOrNull()!!

        val replacement = AvailabilityException(Uuid.random(), id, LocalDate(2026, 10, 22), closed = true)
        admin.saveSchedule(id, listOf(rule(id, DayOfWeek.TUESDAY, 9, 12)), listOf(replacement)).getOrNull()!!

        assertEquals(listOf(DayOfWeek.TUESDAY), ruleRepo.findByService(id).map { it.dayOfWeek })
        assertEquals(listOf(replacement.id), exceptionRepo.findByService(id).map { it.id })
        assertTrue(cancelled.isEmpty())
        assertEquals(1, reservationRepo.findActiveSlotReservations(id, now, LocalDateTime(2027, 1, 1, 0, 0)).size)
    }

    @Test
    fun invalidScheduleIsRejected() = runBlocking {
        val id = created()
        val overlapping = listOf(
            rule(id, DayOfWeek.TUESDAY, 8, 12, validFrom = LocalDate(2026, 1, 1), validTo = LocalDate(2026, 12, 31)),
            rule(id, DayOfWeek.TUESDAY, 9, 11, validFrom = LocalDate(2026, 11, 1)),
            rule(id, DayOfWeek.MONDAY, 12, 10, validFrom = LocalDate(2026, 11, 1)),
        )
        val error = admin.saveSchedule(id, overlapping, emptyList()).leftOrNull()
        assertIs<SlotAdminError.InvalidSchedule>(error)
        assertEquals(setOf(ScheduleProblem.PERIODS_OVERLAP, ScheduleProblem.WINDOW_EMPTY), error.problems.toSet())
        assertEquals(2, ruleRepo.findByService(id).size)
    }

    @Test
    fun dayOverviewHasAllStepsAndTheDaysReservationsByStart() = runBlocking {
        val id = created()
        reserve(id, LocalDateTime(2026, 10, 20, 10, 0), LocalDateTime(2026, 10, 20, 11, 0), name = "Druhá")
        reserve(id, LocalDateTime(2026, 10, 20, 8, 0), LocalDateTime(2026, 10, 20, 9, 30), name = "První")
        reserve(id, LocalDateTime(2026, 10, 22, 8, 0), LocalDateTime(2026, 10, 22, 9, 0), name = "Jiný den")

        val overview = admin.getDayOverview(id, tuesday).getOrNull()!!
        assertEquals(8, overview.segments.size)
        assertEquals(listOf("První", "Druhá"), overview.reservations.map { it.contactName })
        // 8:00 obsazená jednou rezervací se 2 dětmi z kapacity 4.
        assertEquals(2, overview.segments.first().free)
    }

    private class RecordingAudit(private val inner: InMemoryAuditRepository = InMemoryAuditRepository()) : AuditRepository by inner {
        val types = mutableListOf<AuditEventType>()
        override suspend fun record(event: NewAuditEvent) {
            types += event.type
            inner.record(event)
        }
    }
}

class SlotValidationSpec {

    @Test
    fun rulesRoundTripThroughPeriods() {
        val serviceId = Uuid.random()
        val rules = listOf(
            AvailabilityRule(Uuid.random(), serviceId, DayOfWeek.TUESDAY, LocalTime(13, 0), LocalTime(15, 0), LocalDate(2026, 9, 1), LocalDate(2027, 1, 31)),
            AvailabilityRule(Uuid.random(), serviceId, DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(12, 0), LocalDate(2026, 9, 1), LocalDate(2027, 1, 31)),
            AvailabilityRule(Uuid.random(), serviceId, DayOfWeek.MONDAY, LocalTime(8, 0), LocalTime(10, 0), LocalDate(2027, 2, 1), null),
        )
        val periods = rulesToPeriods(rules)
        assertEquals(listOf(LocalDate(2026, 9, 1), LocalDate(2027, 2, 1)), periods.map { it.validFrom })
        // Okna dne seřazená podle začátku.
        assertEquals(listOf(LocalTime(8, 0), LocalTime(13, 0)), periods.first().windows[DayOfWeek.TUESDAY]!!.map { it.from })

        val back = periodsToRules(serviceId, periods)
        assertEquals(
            rules.map { listOf(it.dayOfWeek, it.from, it.to, it.validFrom, it.validTo) }.toSet(),
            back.map { listOf(it.dayOfWeek, it.from, it.to, it.validFrom, it.validTo) }.toSet(),
        )
    }

    @Test
    fun adjacentPeriodsDoNotOverlap() {
        val serviceId = Uuid.random()
        val rules = listOf(
            AvailabilityRule(Uuid.random(), serviceId, DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(12, 0), LocalDate(2026, 9, 1), LocalDate(2027, 1, 31)),
            AvailabilityRule(Uuid.random(), serviceId, DayOfWeek.TUESDAY, LocalTime(9, 0), LocalTime(12, 0), LocalDate(2027, 2, 1), null),
        )
        assertTrue(validateSchedule(rules, emptyList()).isEmpty())
    }

    @Test
    fun exceptionNeedsAWindowUnlessClosed() {
        val serviceId = Uuid.random()
        val day = LocalDate(2026, 10, 22)
        assertEquals(
            listOf(ScheduleProblem.EXCEPTION_WINDOW_MISSING),
            validateSchedule(emptyList(), listOf(AvailabilityException(Uuid.random(), serviceId, day, closed = false))),
        )
        assertTrue(validateSchedule(emptyList(), listOf(AvailabilityException(Uuid.random(), serviceId, day, closed = true))).isEmpty())
    }

    @Test
    fun freeServiceNeedsNoPaymentType() {
        val service = SlotService(
            id = Uuid.random(), title = "Zdarma", description = "", step = 15.minutes,
            minDuration = 15.minutes, maxDuration = 2.hours, pricePerHour = 0.0, capacity = 2,
            maxSeatsPerBooking = 2, allowedPaymentTypes = emptyList(), bookingHorizon = 7.days,
        )
        assertTrue(validateSlotService(service).isEmpty())
        assertEquals(listOf(SlotServiceProblem.NO_PAYMENT_TYPE), validateSlotService(service.copy(pricePerHour = 100.0)))
    }

    private val hourly = SlotService(
        id = Uuid.random(), title = "Hlídání", description = "", step = 60.minutes,
        minDuration = 2.hours, maxDuration = 4.hours, pricePerHour = 150.0, capacity = 4,
        maxSeatsPerBooking = 2, bookingHorizon = 14.days,
    )

    @Test
    fun hourStepIsAllowedAndDurationsMustBeWholeHours() {
        assertTrue(validateSlotService(hourly).isEmpty())
        assertEquals(
            listOf(SlotServiceProblem.MIN_DURATION_INVALID, SlotServiceProblem.MAX_DURATION_INVALID),
            validateSlotService(hourly.copy(minDuration = 90.minutes, maxDuration = 150.minutes)),
        )
        assertEquals(listOf(SlotServiceProblem.STEP_INVALID), validateSlotService(hourly.copy(step = 45.minutes)))
    }

    @Test
    fun zeroMinimumMeansNoMinimum() {
        assertTrue(validateSlotService(hourly.copy(minDuration = Duration.ZERO)).isEmpty())
        assertEquals(listOf(SlotServiceProblem.MIN_DURATION_INVALID), validateSlotService(hourly.copy(minDuration = (-60).minutes)))
    }

    @Test
    fun missingMaximumMeansNoLimit() {
        assertTrue(validateSlotService(hourly.copy(maxDuration = null)).isEmpty())
        assertTrue(validateSlotService(hourly.copy(minDuration = Duration.ZERO, maxDuration = null)).isEmpty())
        // Zadané maximum musí být aspoň jeden krok, i když je minimum 0.
        assertEquals(
            listOf(SlotServiceProblem.MAX_DURATION_INVALID),
            validateSlotService(hourly.copy(minDuration = Duration.ZERO, maxDuration = Duration.ZERO)),
        )
        assertEquals(listOf(SlotServiceProblem.MAX_DURATION_INVALID), validateSlotService(hourly.copy(maxDuration = 1.hours)))
    }
}
