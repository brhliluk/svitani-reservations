package cz.svitaninymburk.projects.reservations.service

import org.simplejavamail.converter.EmailConverter
import org.simplejavamail.email.EmailBuilder
import kotlin.test.Test
import kotlin.test.assertContains

/**
 * Hlídá tvar MIME zprávy, na kterém závisí klienti: HTML odkazuje na QR přes `cid`
 * a Gmail nabídne „Přidat do kalendáře“ jen u `text/calendar` s `method=REQUEST`.
 */
class EmailMimeSpec {

    private fun reservationEml(): String {
        val email = EmailBuilder.startingBlank()
            .from("Svítání", "odesilatel@example.com")
            .addTo("prijemce@example.com")
            .withSubject("Potvrzení rezervace — Cvičení")
        val cid = email.embedPaymentQr(byteArrayOf(1, 2, 3))
        email.attachIcal("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n".toByteArray())
        email.withHTMLText("<html><body><img src=\"cid:$cid\"></body></html>")
        email.withPlainText("Rezervace")
        return EmailConverter.emailToEML(email.buildEmail())
    }

    @Test
    fun `payment QR is embedded under the cid the HTML references`() {
        val eml = reservationEml()
        assertContains(eml, "src=3D\"cid:$PAYMENT_QR_CID\"")
        assertContains(eml, "Content-ID: <$PAYMENT_QR_CID>")
        assertContains(eml, "Content-Type: image/png")
    }

    @Test
    fun `iCal goes out as a calendar request and as a downloadable attachment`() {
        val eml = reservationEml()
        assertContains(eml, "Content-Type: text/calendar; charset=\"UTF-8\"; method=\"REQUEST\"")
        assertContains(eml, "Content-Disposition: attachment; filename=rezervace.ics")
    }
}
