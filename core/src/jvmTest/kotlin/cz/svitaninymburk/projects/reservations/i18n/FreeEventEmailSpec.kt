package cz.svitaninymburk.projects.reservations.i18n

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Potvrzovací e-mail na akci zdarma nemá uvádět částku „0.0 Kč" — v tom by
 * uživatel hledal, co má zaplatit.
 */
class FreeEventEmailSpec {

    private fun csBody(totalPrice: Double) = CsEmailStrings.reservationConfirmationBody(
        eventTitle = "Beseda",
        eventDate = "1. 6. 2099 10:00",
        contactName = "Jan Novák",
        seatCount = 1,
        totalPrice = totalPrice,
    )

    private fun enBody(totalPrice: Double) = EnEmailStrings.reservationConfirmationBody(
        eventTitle = "Talk",
        eventDate = "June 1, 2099 10:00",
        contactName = "Jan Novak",
        seatCount = 1,
        totalPrice = totalPrice,
    )

    @Test
    fun `czech confirmation for a free event says free instead of zero`() {
        val body = csBody(0.0)
        assertTrue(body.contains("Zdarma"), "Chybí 'Zdarma' v: $body")
        assertFalse(body.contains("0.0"), "Nulová cena nemá být vypsaná jako číslo: $body")
    }

    @Test
    fun `english confirmation for a free event says free instead of zero`() {
        val body = enBody(0.0)
        assertTrue(body.contains("Free"), "Chybí 'Free' v: $body")
        assertFalse(body.contains("0.0"), "Nulová cena nemá být vypsaná jako číslo: $body")
    }

    @Test
    fun `confirmation for a paid event still states the amount`() {
        assertTrue(csBody(150.0).contains("150"), "Chybí částka v: ${csBody(150.0)}")
        assertTrue(csBody(150.0).contains("Kč"), "Chybí měna v: ${csBody(150.0)}")
        assertTrue(enBody(150.0).contains("150"), "Chybí částka v: ${enBody(150.0)}")
        assertTrue(enBody(150.0).contains("CZK"), "Chybí měna v: ${enBody(150.0)}")
    }
}
