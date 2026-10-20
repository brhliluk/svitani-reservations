package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.i18n.CsEmailStrings
import cz.svitaninymburk.projects.reservations.i18n.EnEmailStrings
import cz.svitaninymburk.projects.reservations.i18n.LectorTarget
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** U rezervace úseku (hlídání) nesou pozvánka do kalendáře i e-maily celý interval, ne jen začátek. */
class SlotEmailContentSpec {

    private val start = LocalDateTime(2026, 10, 20, 9, 0)
    private val end = LocalDateTime(2026, 10, 20, 11, 30)

    private val target = ReservationTarget.Slot(
        serviceId = Uuid.random(),
        title = "Hlídání dětí",
        startDateTime = start,
        endDateTime = end,
        pricePerHour = 150.0,
        freeSeats = 3,
        capacity = 8,
        maxSeatsPerBooking = 3,
        allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
        customFields = emptyList(),
    )

    @Test
    fun `calendar invite spans the booked interval`() {
        val ical = ICalGenerator.forTarget(target, Uuid.random(), "https://example.cz")

        assertContains(ical, "SUMMARY:Hlídání dětí")
        assertContains(ical, "20261020T0900")
        assertContains(ical, "20261020T1130")
    }

    @Test
    fun `customer emails show start and end`() {
        assertEquals("20.10.2026 9:00–11:30", target.whenLabel())
    }

    @Test
    fun `lector emails show the interval in both languages`() {
        val slot = LectorTarget.Slot(start, end)

        assertContains(CsEmailStrings.lectorReservationSubject("Hlídání dětí", slot), "20.10.2026 9:00–11:30")
        assertContains(CsEmailStrings.lectorReservationBody("Jana", "jana@test.cz", null, 2, "Hlídání dětí", slot, 5, 8), "5 / 8")
        assertContains(EnEmailStrings.lectorCancellationBody("Jana", "Hlídání dětí", slot, 2, 3, 8), "20.10.2026 9:00–11:30")
    }
}
