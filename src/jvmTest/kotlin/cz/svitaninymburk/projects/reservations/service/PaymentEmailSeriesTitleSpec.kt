package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Přihláška na kurz je rezervace na *sérii*, ne na instanci. Platební maily ale
 * název hledaly jen mezi lekcemi, takže uživateli přišlo „Vaše rezervace na akci:
 * null byla zaplacena". U jednorázové akce (rezervace na instanci) chodil název
 * správně, proto si toho nikdo dlouho nevšiml.
 */
class PaymentEmailSeriesTitleSpec {

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()

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
        eventRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
    )

    private fun reservation(reference: Reference) = Reservation(
        id = Uuid.random(),
        reference = reference,
        contactName = "Jan Novák",
        contactEmail = "jan@example.test",
        totalPrice = 1500.0,
        status = Reservation.Status.PENDING_PAYMENT,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.BANK_TRANSFER,
    )

    @Test
    fun `course reservation knows the series title`() = runBlocking {
        val seriesId = Uuid.random()
        seriesRepo.create(
            EventSeries(
                id = seriesId,
                definitionId = Uuid.random(),
                title = "Fit&Play",
                description = "",
                price = 1500.0,
                capacity = 10,
                occupiedSpots = 1,
                startDate = LocalDate(2026, 9, 1),
                endDate = LocalDate(2026, 12, 31),
                lessonCount = 12,
            )
        )

        assertEquals("Fit&Play", service().titleFor(reservation(Reference.Series(seriesId))))
    }

    @Test
    fun `single lesson reservation knows the lesson title`() = runBlocking {
        val instanceId = Uuid.random()
        instanceRepo.create(
            EventInstance(
                id = instanceId,
                definitionId = Uuid.random(),
                seriesId = null,
                title = "Podpůrná skupina",
                description = "",
                startDateTime = LocalDateTime(2026, 10, 1, 10, 0),
                endDateTime = LocalDateTime(2026, 10, 1, 11, 0),
                price = 200.0,
                capacity = 10,
            )
        )

        assertEquals("Podpůrná skupina", service().titleFor(reservation(Reference.Instance(instanceId))))
    }
}
