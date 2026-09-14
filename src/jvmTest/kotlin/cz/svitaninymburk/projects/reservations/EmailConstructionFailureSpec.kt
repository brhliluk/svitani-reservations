package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.service.GmailEmailService
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regrese k incidentu z 18. 8. 2026: e-mail vlastníka lekce byl uložený s koncovým
 * středníkem, `HtmlEmail.addTo()` ho odmítl výjimkou — a protože `addTo()` stálo *mimo*
 * blok `catch({ email.send() })`, výjimka propadla celým `createReservationFlow`,
 * Kilua ji vrátila jako chybovou obálku a frontend na ní uvíznul s nekonečným spinnerem.
 *
 * Chyba při *konstrukci* e-mailu musí skončit jako `Either.Left`, ne jako vyhozená
 * výjimka. Adresa se validuje ještě před navázáním SMTP spojení, takže tyto testy
 * nechodí po síti.
 */
class EmailConstructionFailureSpec {

    private fun service() = GmailEmailService(
        settings = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "2003487968/2010",
                fioToken = "",
                senderEmail = "odesilatel@svitaninymburk.cz",
                gmailAppPassword = "",
                senderDisplayName = "Rodinné centrum Svítání",
            )
        ),
        appBaseUrl = "https://example.test",
        eventRepository = InMemoryEventInstanceRepository(),
        eventSeriesRepository = InMemoryEventSeriesRepository(),
    )

    @Test
    fun lectorNotificationWithTrailingSemicolonReturnsLeftInsteadOfThrowing() = runBlocking {
        val result = service().sendLectorReservationNotification(
            lectorEmail = "dagmar.stinga@svitaninymburk.cz;",
            contactName = "Andrea Beranová",
            contactEmail = "andrea@example.test",
            contactPhone = null,
            seatCount = 1,
            eventTitle = "Mikrobit a 3D tisk",
            occupiedSpots = 3,
            capacity = 10,
            locale = "cs",
        )

        assertTrue(result.isLeft(), "malformed owner address must yield Left, not throw: $result")
    }

    @Test
    fun cancellationNoticeWithMalformedAddressReturnsLeft() = runBlocking {
        val result = service().sendCancellationNotice(
            toEmail = "not-an-address;",
            eventTitle = "Mikrobit a 3D tisk",
            reservationId = kotlin.uuid.Uuid.random(),
            locale = "cs",
        )

        assertTrue(result.isLeft(), "malformed recipient must yield Left, not throw: $result")
    }
}
