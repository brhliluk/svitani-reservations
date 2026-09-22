package cz.svitaninymburk.projects.reservations.qr

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SpaydMessageSpec {

    private val accountNumber = "2003487968/2010"

    @Test
    fun `zprava ztrati diakritiku, ale zustane citelna`() {
        assertEquals("Cviceni s miminky", SpaydGenerator.sanitizeMessage("Cvičení s miminky"))
    }

    @Test
    fun `interpunkce se nahradi mezerou a mezery se nezdvoji`() {
        assertEquals("Predporodni kurz podzim", SpaydGenerator.sanitizeMessage("Předporodní kurz – podzim!"))
    }

    /** `*` odděluje ve SPAYD pole — kdyby prošla, rozbila by celý řetězec. */
    @Test
    fun `hvezdicka a procento ze zpravy zmizi a nerozbiji format`() {
        assertEquals("Joga pro maminky 100", SpaydGenerator.sanitizeMessage("Jóga*pro*maminky 100%"))
        val spayd = SpaydGenerator.generate("CZ00", 100.0, "2612300001", "Jóga*pro*maminky 100%")
        assertEquals(7, spayd.split("*").size) // SPD, 1.0, ACC, AM, CC, X-VS, MSG
    }

    @Test
    fun `zprava se orezava az po odstraneni diakritiky`() {
        val sanitized = SpaydGenerator.sanitizeMessage("Ě".repeat(70))
        assertEquals("E".repeat(SpaydGenerator.MAX_MESSAGE_LENGTH), sanitized)
    }

    @Test
    fun `zprava, ze ktere nic nezbylo, se do SPAYD nedostane`() {
        val spayd = SpaydGenerator.generate("CZ00", 100.0, "2612300001", "!!!")
        assertFalse(spayd.contains("MSG"), spayd)
    }

    /**
     * E-mail, web i mobilní API musí u jedné rezervace vydat stejný QR kód. Dřív si
     * každý kanál skládal SPAYD po svém a zprávu vyplňoval jinak (nebo vůbec).
     */
    @Test
    fun `nazev akce jde do zpravy pro prijemce`() {
        val service = QrCodeService()
        val target = instanceTarget("Cvičení s miminky")
        val reservation = reservation(totalPrice = 500.0)

        val spayd = service.reservationSpayd(reservation, target, accountNumber)

        assertTrue(spayd.contains("*MSG:Cviceni s miminky"), spayd)
        assertTrue(spayd.contains("*AM:500.00"), spayd)
        assertTrue(spayd.contains("*X-VS:2612300001"), spayd)
    }

    @Test
    fun `QR pro prohlizec vychazi ze stejneho SPAYD jako ostatni kanaly`() {
        val service = QrCodeService()
        val target = instanceTarget("Cvičení s miminky")
        val reservation = reservation(totalPrice = 500.0)

        val spayd = service.reservationSpayd(reservation, target, accountNumber)
        assertEquals(service.generateQrSvg(spayd), service.generateReservationPaymentSvg(reservation, target, accountNumber))
    }

    @Test
    fun `bez dohledane akce se posle QR bez zpravy`() {
        val spayd = QrCodeService().reservationSpayd(reservation(totalPrice = 500.0), null, accountNumber)
        assertFalse(spayd.contains("MSG"), spayd)
    }

    @Test
    fun `castka je nedoplatek, ne cela cena`() {
        val spayd = QrCodeService()
            .reservationSpayd(reservation(totalPrice = 500.0, paidAmount = 200.0), instanceTarget("Kurz"), accountNumber)
        assertTrue(spayd.contains("*AM:300.00"), spayd)
    }

    private fun instanceTarget(title: String) = ReservationTarget.Instance(
        EventInstance(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            title = title,
            description = "",
            startDateTime = LocalDateTime(2026, 10, 5, 9, 0),
            endDateTime = LocalDateTime(2026, 10, 5, 10, 0),
            price = 500.0,
            capacity = 10,
        )
    )

    private fun reservation(totalPrice: Double, paidAmount: Double = 0.0) = Reservation(
        id = Uuid.random(),
        reference = Reference.Instance(Uuid.random()),
        contactName = "Jana Nováková",
        contactEmail = "jana@example.com",
        seatCount = 1,
        totalPrice = totalPrice,
        paidAmount = paidAmount,
        status = Reservation.Status.PENDING_PAYMENT,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.BANK_TRANSFER,
        variableSymbol = "2612300001",
    )
}
