package cz.svitaninymburk.projects.reservations.slot.ui

import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.slot.DayAvailability
import cz.svitaninymburk.projects.reservations.slot.SlotSegment
import cz.svitaninymburk.projects.reservations.slot.SlotService
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.CsSlotStrings
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.EndLimit
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotPick
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotSelection
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.availabilityRange
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.dayHasAvailability
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.endLimit
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.initialDay
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.invalidatesSelection
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.isSelectionLongEnough
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.isStartAvailable
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.isStepSelectable
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.keepPick
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.maxSeatsFor
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.pickLast
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.pickStart
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.possibleLasts
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionHours
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionPrice
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionTarget
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.toCreateSlotReservationRequest
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.windows
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private val DAY = LocalDate(2026, 10, 14)

private fun service(
    minDuration: kotlin.time.Duration = 30.minutes,
    maxDuration: kotlin.time.Duration? = 2.hours,
    maxSeatsPerBooking: Int = 3,
    capacity: Int = 8,
) = SlotService(
    id = Uuid.random(),
    title = "Hlídání",
    description = "",
    step = 30.minutes,
    minDuration = minDuration,
    maxDuration = maxDuration,
    pricePerHour = 150.0,
    capacity = capacity,
    maxSeatsPerBooking = maxSeatsPerBooking,
    bookingHorizon = 14.days,
    isPublished = true,
)

private fun step(hour: Int, minute: Int, free: Int, capacity: Int = 8): SlotSegment {
    val start = LocalDateTime(DAY.year, DAY.month, DAY.day, hour, minute)
    val endMinutes = hour * 60 + minute + 30
    val end = LocalDateTime(DAY.year, DAY.month, DAY.day, endMinutes / 60, endMinutes % 60)
    return SlotSegment(start, end, free, capacity)
}

/** 8:00–10:00 po půlhodinách, v 9:00 jen 1 volné místo; pak zavřeno a okno 11:00–12:00. */
private val segments = listOf(
    step(8, 0, free = 5),
    step(8, 30, free = 5),
    step(9, 0, free = 1),
    step(9, 30, free = 5),
    // mezera = hranice mezi okny
    step(11, 0, free = 5),
    step(11, 30, free = 0),
)

class SlotSelectionUseCasesSpec {

    @Test
    fun stepIsSelectableOnlyWithRoomForAllChildren() {
        assertTrue(isStepSelectable(step(9, 0, free = 2), seats = 2))
        assertFalse(isStepSelectable(step(9, 0, free = 1), seats = 2))
        assertFalse(isStepSelectable(step(9, 0, free = 0), seats = 1))
    }

    @Test
    fun departuresRunUntilTheNextFullStep() {
        // Dvě děti: v 9:00 je jen jedno místo, z 8:00 tedy nejpozději do 9:00.
        assertEquals(0..1, possibleLasts(0, segments, 2, service()))
        assertEquals(EndLimit.Full(segments[2].start), endLimit(0, segments, 2, service()))
    }

    @Test
    fun departuresStopAtTheEndOfTheWindow() {
        assertEquals(3..3, possibleLasts(3, segments, 1, service()))
        assertEquals(EndLimit.Closes(segments[3].end), endLimit(3, segments, 1, service()))
    }

    @Test
    fun departuresRespectTheMaximumLength() {
        val oneHour = service(maxDuration = 1.hours)
        assertEquals(0..1, possibleLasts(0, segments, 1, oneHour))
        assertEquals(EndLimit.MaxDuration(1.hours), endLimit(0, segments, 1, oneHour))
    }

    @Test
    fun withoutMaximumTheDeparturesAreLimitedOnlyByTheWindow() {
        val unlimited = service(maxDuration = null)
        assertEquals(0..3, possibleLasts(0, segments, 1, unlimited))
        assertEquals(EndLimit.Closes(segments[3].end), endLimit(0, segments, 1, unlimited))
    }

    @Test
    fun departuresStartAtTheMinimumLength() {
        assertEquals(1..3, possibleLasts(0, segments, 1, service(minDuration = 1.hours)))
    }

    @Test
    fun arrivalIsAvailableOnlyWhenTheShortestBookingFits() {
        assertTrue(isStartAvailable(0, segments, 1, service()))
        // Plný krok.
        assertFalse(isStartAvailable(2, segments, 2, service()))
        assertNull(endLimit(2, segments, 2, service()))
        // V 9:30 je volno, ale do konce okna zbývá jen půl hodiny.
        assertFalse(isStartAvailable(3, segments, 1, service(minDuration = 1.hours)))
        assertTrue(possibleLasts(99, segments, 1, service()).isEmpty())
    }

    @Test
    fun firstArrivalStartsThePick() {
        assertEquals(SlotPick(1), pickStart(null, 1, segments, 2, service()))
    }

    @Test
    fun tapOnTheSelectedArrivalClearsThePick() {
        assertNull(pickStart(SlotPick(1, 3), 1, segments, 1, service()))
    }

    @Test
    fun unavailableArrivalKeepsThePick() {
        assertEquals(SlotPick(0, 1), pickStart(SlotPick(0, 1), 2, segments, 2, service()))
        assertNull(pickStart(null, 5, segments, 1, service()))
    }

    @Test
    fun newArrivalKeepsTheDepartureOnlyWhenItStillFits() {
        assertEquals(SlotPick(1, 3), pickStart(SlotPick(0, 3), 1, segments, 1, service()))
        assertEquals(SlotPick(3), pickStart(SlotPick(0, 1), 3, segments, 1, service()))
    }

    @Test
    fun departureTapSelectsAndSecondTapClearsIt() {
        val withDeparture = pickLast(SlotPick(0), 3, segments, 1, service())
        assertEquals(SlotPick(0, 3), withDeparture)
        assertEquals(SlotSelection(0, 3), withDeparture.selection)
        assertEquals(SlotPick(0), pickLast(withDeparture, 3, segments, 1, service()))
        assertNull(SlotPick(0).selection)
    }

    @Test
    fun departureThatIsNotOfferedKeepsThePick() {
        // Přes mezeru mezi okny ani přes plný krok to nejde.
        assertEquals(SlotPick(0, 1), pickLast(SlotPick(0, 1), 4, segments, 1, service(maxDuration = null)))
        assertEquals(SlotPick(0), pickLast(SlotPick(0), 3, segments, 2, service()))
    }

    @Test
    fun moreChildrenKeepWhatStillFits() {
        assertEquals(SlotPick(0, 1), keepPick(SlotPick(0, 1), segments, 2, service()))
        assertEquals(SlotPick(0), keepPick(SlotPick(0, 3), segments, 2, service()))
        assertNull(keepPick(SlotPick(2, 2), segments, 2, service()))
        assertNull(keepPick(null, segments, 2, service()))
    }

    @Test
    fun daySplitsIntoWindowsAtGaps() {
        assertEquals(listOf(0..3, 4..5), windows(segments))
        assertEquals(emptyList<IntRange>(), windows(emptyList()))
    }

    @Test
    fun departureLimitMessageNamesTheTime() {
        assertTrue(CsSlotStrings.endLimit(EndLimit.Full(segments[2].start)).contains("9:00"))
        assertTrue(CsSlotStrings.endLimit(EndLimit.Closes(segments[3].end)).contains("10:00"))
        assertTrue(CsSlotStrings.endLimit(EndLimit.MaxDuration(2.hours)).contains("2 h"))
    }

    @Test
    fun selectionMustReachTheMinimumLength() {
        val oneHourMinimum = service(minDuration = 1.hours)
        assertFalse(isSelectionLongEnough(SlotSelection(0, 0), segments, oneHourMinimum))
        assertTrue(isSelectionLongEnough(SlotSelection(0, 1), segments, oneHourMinimum))
    }

    @Test
    fun zeroMinimumAcceptsASingleStep() {
        val noMinimum = service(minDuration = kotlin.time.Duration.ZERO)
        assertTrue(isSelectionLongEnough(SlotSelection(0, 0), segments, noMinimum))
        assertTrue(dayHasAvailability(DayAvailability(DAY, listOf(step(9, 30, free = 1))), seats = 1, minDuration = noMinimum.minDuration))
    }

    @Test
    fun hourStepsAreSelectedAndPricedAsWholeHours() {
        val hourSteps = (8..11).map { hour ->
            SlotSegment(LocalDateTime(DAY.year, DAY.month, DAY.day, hour, 0), LocalDateTime(DAY.year, DAY.month, DAY.day, hour + 1, 0), 4, 8)
        }
        val hourly = service(minDuration = kotlin.time.Duration.ZERO, maxDuration = 3.hours).copy(step = 60.minutes)

        assertEquals(0..2, possibleLasts(0, hourSteps, 1, hourly))
        assertEquals(EndLimit.MaxDuration(3.hours), endLimit(0, hourSteps, 1, hourly))
        assertEquals(3.0, selectionHours(SlotSelection(0, 2), hourSteps))
        assertTrue(isSelectionLongEnough(SlotSelection(1, 1), hourSteps, hourly))
    }

    @Test
    fun hoursAndPriceAreExact() {
        assertEquals(1.5, selectionHours(SlotSelection(0, 2), segments))
        // 1,5 h × 2 děti × 150 Kč, bez zaokrouhlení.
        assertEquals(450.0, selectionPrice(service(), seats = 2, hours = 1.5))
        assertEquals(337.5, selectionPrice(service(), seats = 1, hours = 2.25))
    }

    @Test
    fun dayHasAvailabilityWhenSomeRunIsLongEnough() {
        val day = DayAvailability(DAY, segments)
        assertTrue(dayHasAvailability(day, seats = 2))
        // Pro dvě děti jsou nejdelší souvislé běhy 8:00–9:00 a 9:30–10:00, a 11:00–11:30.
        assertTrue(dayHasAvailability(day, seats = 2, minDuration = 1.hours))
        assertFalse(dayHasAvailability(day, seats = 2, minDuration = 90.minutes))
        assertFalse(dayHasAvailability(day, seats = 6))
    }

    @Test
    fun runDoesNotContinueAcrossTheGapBetweenWindows() {
        val day = DayAvailability(DAY, listOf(step(9, 30, free = 5), step(11, 0, free = 5)))
        assertFalse(dayHasAvailability(day, seats = 1, minDuration = 1.hours))
    }

    @Test
    fun initialDayIsTheFirstOneWithRoom() {
        val full = DayAvailability(LocalDate(2026, 10, 13), listOf(step(8, 0, free = 0)))
        val free = DayAvailability(DAY, segments)
        assertEquals(DAY, initialDay(listOf(full, free), seats = 1, minDuration = 30.minutes))
        assertEquals(full.date, initialDay(listOf(full), seats = 1, minDuration = 30.minutes))
        assertNull(initialDay(emptyList(), seats = 1, minDuration = 30.minutes))
    }

    @Test
    fun seatLimitNeverExceedsCapacity() {
        assertEquals(3, maxSeatsFor(service(maxSeatsPerBooking = 3, capacity = 8)))
        assertEquals(2, maxSeatsFor(service(maxSeatsPerBooking = 5, capacity = 2)))
    }

    @Test
    fun availabilityIsRequestedUpToTheHorizonPlusADay() {
        val range = availabilityRange(DAY, service())
        assertEquals(DAY, range.start)
        assertEquals(LocalDate(2026, 10, 29), range.endInclusive)
    }

    @Test
    fun targetCarriesTheIntervalAndTheLowestFreeCount() {
        val service = service()
        val target = selectionTarget(service, SlotSelection(0, 3), segments)
        assertEquals(segments[0].start, target.startDateTime)
        assertEquals(segments[3].end, target.endDateTime)
        assertEquals(1, target.freeSeats)
        assertEquals(service.id, target.serviceId)
        assertEquals(150.0, target.price)
    }

    @Test
    fun formDataBecomesASlotRequest() {
        val target = selectionTarget(service(), SlotSelection(0, 1), segments)
        val form = ReservationFormData(
            name = "Jana",
            surname = "Nováková",
            email = "jana@example.cz",
            phone = "+420 777 111 222",
            seats = 2,
            paymentType = PaymentType.ON_SITE,
            customValues = mapOf("poznamka" to TextValue("poznamka", "přivedu 8:10")),
            walletCode = "SVIT-AAAA-BBBB",
        )
        val request = form.toCreateSlotReservationRequest(target, acknowledgedDuplicate = true)
        assertEquals(target.serviceId, request.serviceId)
        assertEquals(segments[0].start, request.start)
        assertEquals(segments[1].end, request.end)
        assertEquals(2, request.seatCount)
        assertEquals("Jana Nováková", request.contactName)
        assertEquals("SVIT-AAAA-BBBB", request.walletCode)
        assertTrue(request.acknowledgedDuplicate)
    }

    @Test
    fun onlyAvailabilityErrorsResetTheSelection() {
        assertTrue(invalidatesSelection(ReservationError.SlotCapacityExceeded))
        assertTrue(invalidatesSelection(ReservationError.ReservationDeadlinePassed))
        assertFalse(invalidatesSelection(ReservationError.PaymentTypeNotAllowed))
    }

    @Test
    fun czechChildrenAgreeWithTheCount() {
        assertEquals("1 dítě", CsSlotStrings.children(1))
        assertEquals("3 děti", CsSlotStrings.children(3))
        assertEquals("5 dětí", CsSlotStrings.children(5))
        assertEquals("2,25 h", CsSlotStrings.hours(2.25))
        assertEquals("1 h 30 min", CsSlotStrings.duration(90.minutes))
    }
}
