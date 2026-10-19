package cz.svitaninymburk.projects.reservations.slot

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class ExpandAvailabilitySpec {

    private val service = SlotService(
        id = Uuid.random(),
        title = "Hlídání",
        description = "",
        step = 30.minutes,
        minDuration = 1.hours,
        maxDuration = 4.hours,
        pricePerHour = 150.0,
        capacity = 8,
        maxSeatsPerBooking = 3,
        bookingHorizon = 28.days,
    )

    // 20. 10. 2026 je úterý, 22. 10. čtvrtek.
    private val tuesday = LocalDate(2026, 10, 20)
    private val wednesday = LocalDate(2026, 10, 21)
    private val thursday = LocalDate(2026, 10, 22)
    private val nextTuesday = LocalDate(2026, 10, 27)

    private fun rule(
        day: DayOfWeek,
        from: LocalTime,
        to: LocalTime,
        validFrom: LocalDate = LocalDate(2026, 1, 1),
        validTo: LocalDate? = null,
        serviceId: Uuid = service.id,
    ) = AvailabilityRule(Uuid.random(), serviceId, day, from, to, validFrom, validTo)

    private fun closedOn(date: LocalDate) =
        AvailabilityException(Uuid.random(), service.id, date, closed = true)

    private fun openOn(date: LocalDate, from: LocalTime, to: LocalTime) =
        AvailabilityException(Uuid.random(), service.id, date, closed = false, from = from, to = to)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(date, LocalTime(hour, minute))

    private fun expand(
        rules: List<AvailabilityRule>,
        exceptions: List<AvailabilityException> = emptyList(),
        booked: List<BookedInterval> = emptyList(),
        from: LocalDate = tuesday,
        to: LocalDate = tuesday,
        svc: SlotService = service,
    ) = expandAvailability(svc, rules, exceptions, booked, from, to)

    private fun DayAvailability.startTimes() = segments.map { it.start.time }

    @Test
    fun windowIsSplitIntoSteps() {
        val days = expand(listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(10, 0))))

        assertEquals(1, days.size)
        assertEquals(tuesday, days.single().date)
        assertEquals(
            listOf(LocalTime(8, 0), LocalTime(8, 30), LocalTime(9, 0), LocalTime(9, 30)),
            days.single().startTimes(),
        )
        val last = days.single().segments.last()
        assertEquals(at(tuesday, 10), last.end)
        assertTrue(days.single().segments.all { it.free == 8 && it.capacity == 8 })
    }

    @Test
    fun multipleWindowsPerDayKeepGap() {
        val days = expand(
            listOf(
                rule(DayOfWeek.TUESDAY, LocalTime(14, 0), LocalTime(15, 0)),
                rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(9, 0)),
            )
        )

        assertEquals(
            listOf(LocalTime(8, 0), LocalTime(8, 30), LocalTime(14, 0), LocalTime(14, 30)),
            days.single().startTimes(),
        )
    }

    @Test
    fun overlappingAndAdjacentWindowsAreMerged() {
        val days = expand(
            listOf(
                rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(10, 0)),
                rule(DayOfWeek.TUESDAY, LocalTime(9, 0), LocalTime(11, 0)),
                rule(DayOfWeek.TUESDAY, LocalTime(11, 0), LocalTime(12, 0)),
            )
        )

        val segments = days.single().segments
        // Žádný krok dvakrát a 8–12 bez mezery.
        assertEquals(8, segments.size)
        assertEquals(segments.map { it.start }.distinct(), segments.map { it.start })
        assertTrue(segments.zipWithNext().all { (a, b) -> a.end == b.start })
    }

    @Test
    fun onlyMatchingDaysOfWeekInRange() {
        val days = expand(
            listOf(
                rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(9, 0)),
                rule(DayOfWeek.THURSDAY, LocalTime(8, 0), LocalTime(9, 0)),
            ),
            from = tuesday,
            to = nextTuesday,
        )

        assertEquals(listOf(tuesday, thursday, nextTuesday), days.map { it.date })
    }

    @Test
    fun closedExceptionRemovesDay() {
        val days = expand(
            listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(12, 0))),
            exceptions = listOf(closedOn(tuesday)),
            from = tuesday,
            to = nextTuesday,
        )

        assertEquals(listOf(nextTuesday), days.map { it.date })
    }

    @Test
    fun exceptionWithOtherHoursReplacesAllRulesOfDay() {
        val days = expand(
            listOf(
                rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(12, 0)),
                rule(DayOfWeek.TUESDAY, LocalTime(14, 0), LocalTime(16, 0)),
            ),
            exceptions = listOf(openOn(tuesday, LocalTime(13, 0), LocalTime(14, 0))),
        )

        assertEquals(listOf(LocalTime(13, 0), LocalTime(13, 30)), days.single().startTimes())
    }

    @Test
    fun exceptionOpensDayWithoutRule() {
        val days = expand(
            listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(9, 0))),
            exceptions = listOf(openOn(wednesday, LocalTime(10, 0), LocalTime(11, 0))),
            from = tuesday,
            to = wednesday,
        )

        assertEquals(listOf(tuesday, wednesday), days.map { it.date })
        assertEquals(listOf(LocalTime(10, 0), LocalTime(10, 30)), days.last().startTimes())
    }

    @Test
    fun exceptionWithoutHoursClosesDay() {
        val days = expand(
            listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(12, 0))),
            exceptions = listOf(AvailabilityException(Uuid.random(), service.id, tuesday, closed = false)),
        )

        assertTrue(days.isEmpty())
    }

    @Test
    fun ruleValidityIsInclusive() {
        val rules = listOf(
            rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(9, 0), validFrom = tuesday, validTo = tuesday),
            rule(DayOfWeek.TUESDAY, LocalTime(10, 0), LocalTime(11, 0), validFrom = nextTuesday),
        )

        val days = expand(rules, from = LocalDate(2026, 10, 13), to = LocalDate(2026, 11, 3))

        assertEquals(listOf(tuesday, nextTuesday, LocalDate(2026, 11, 3)), days.map { it.date })
        assertEquals(LocalTime(8, 0), days[0].startTimes().first())
        assertEquals(LocalTime(10, 0), days[1].startTimes().first())
    }

    @Test
    fun rulesAndExceptionsOfOtherServiceAreIgnored() {
        val other = Uuid.random()
        val days = expand(
            listOf(
                rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(9, 0)),
                rule(DayOfWeek.TUESDAY, LocalTime(14, 0), LocalTime(15, 0), serviceId = other),
            ),
            exceptions = listOf(AvailabilityException(Uuid.random(), other, tuesday, closed = true)),
        )

        assertEquals(listOf(LocalTime(8, 0), LocalTime(8, 30)), days.single().startTimes())
    }

    @Test
    fun unalignedWindowIsCutToWholeSteps() {
        val days = expand(listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 10), LocalTime(9, 50))))

        assertEquals(listOf(LocalTime(8, 30), LocalTime(9, 0)), days.single().startTimes())
        assertEquals(at(tuesday, 9, 30), days.single().segments.last().end)
    }

    @Test
    fun windowShorterThanStepGivesNoDay() {
        val days = expand(listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(8, 20))))

        assertTrue(days.isEmpty())
    }

    @Test
    fun bookingSubtractsSeatsFromEveryOverlappedStep() {
        val days = expand(
            listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(11, 0))),
            booked = listOf(
                BookedInterval(at(tuesday, 9), at(tuesday, 10), seats = 2),
                BookedInterval(at(tuesday, 9, 30), at(tuesday, 11), seats = 3),
            ),
        )

        // 8:00 8:30 | 9:00 | 9:30 | 10:00 10:30
        assertEquals(listOf(8, 8, 6, 3, 5, 5), days.single().segments.map { it.free })
    }

    @Test
    fun bookingAcrossStepBoundaryOccupiesBothSteps() {
        val days = expand(
            listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(10, 0))),
            booked = listOf(BookedInterval(at(tuesday, 8, 15), at(tuesday, 8, 45), seats = 1)),
        )

        assertEquals(listOf(7, 7, 8, 8), days.single().segments.map { it.free })
    }

    @Test
    fun bookingTouchingStepDoesNotOccupyIt() {
        val days = expand(
            listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(10, 0))),
            booked = listOf(BookedInterval(at(tuesday, 8, 30), at(tuesday, 9), seats = 4)),
        )

        assertEquals(listOf(8, 4, 8, 8), days.single().segments.map { it.free })
    }

    @Test
    fun bookingOnOtherDayDoesNotCount() {
        val days = expand(
            listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(9, 0))),
            booked = listOf(BookedInterval(at(nextTuesday, 8), at(nextTuesday, 9), seats = 5)),
        )

        assertEquals(listOf(8, 8), days.single().segments.map { it.free })
    }

    @Test
    fun fullStepHasZeroFreeAndOverbookingDoesNotGoNegative() {
        val days = expand(
            listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(9, 0))),
            booked = listOf(
                BookedInterval(at(tuesday, 8), at(tuesday, 8, 30), seats = 8),
                // Přeplnění, např. po snížení kapacity adminem.
                BookedInterval(at(tuesday, 8, 30), at(tuesday, 9), seats = 5),
                BookedInterval(at(tuesday, 8, 30), at(tuesday, 9), seats = 5),
            ),
        )

        assertEquals(listOf(0, 0), days.single().segments.map { it.free })
    }

    @Test
    fun fifteenMinuteStep() {
        val days = expand(
            listOf(rule(DayOfWeek.TUESDAY, LocalTime(8, 0), LocalTime(9, 0))),
            svc = service.copy(step = 15.minutes),
        )

        assertEquals(4, days.single().segments.size)
    }

    @Test
    fun nonPositiveStepIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            expand(emptyList(), svc = service.copy(step = 0.minutes))
        }
    }
}
