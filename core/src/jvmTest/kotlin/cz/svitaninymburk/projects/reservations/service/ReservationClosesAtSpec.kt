package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Uzávěrku rezervace si prohlížeč nespočítá — nemá databázi časových pásem a
 * `TimeZone.of("Europe/Prague")` by v něm spadl. Server ji proto posílá hotovou
 * jako absolutní čas, spočítanou v pražském čase bez ohledu na zónu JVM.
 */
class ReservationClosesAtSpec {

    private var originalDefault: TimeZone? = null

    @BeforeTest
    fun jvmInTokyo() {
        originalDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    }

    @AfterTest
    fun restore() {
        TimeZone.setDefault(originalDefault)
    }

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val service = EventService(InMemoryEventDefinitionRepository(), instanceRepo, seriesRepo)

    // 1. 12. 2099 10:00 v Praze je zima, tedy UTC+1 → 09:00Z; uzávěrka 2 h předem → 07:00Z.
    private val instance = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Beseda",
        description = "",
        startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
        price = 100.0,
        capacity = 10,
        isPublished = true,
        reservationDeadline = 2.hours,
    )

    // Kurz od 1. 7. 2099 s lekcemi v 17:00 — léto, UTC+2 → 15:00Z; uzávěrka 1 h předem → 14:00Z.
    private val series = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Kurz",
        description = "",
        price = 1000.0,
        capacity = 10,
        startDate = LocalDate(2099, 7, 1),
        endDate = LocalDate(2099, 8, 31),
        lessonCount = 8,
        lessonStartTime = LocalTime(17, 0),
        isPublished = true,
        reservationDeadline = 1.hours,
    )

    @Test
    fun `event gets deadline computed in Prague time`() = runBlocking {
        instanceRepo.create(instance)

        assertEquals(Instant.parse("2099-12-01T07:00:00Z"), service.getInstance(instance.id).getOrNull()?.reservationClosesAt)
        assertEquals(
            Instant.parse("2099-12-01T07:00:00Z"),
            service.getDashboardData().getOrNull()?.instances?.single()?.reservationClosesAt,
        )
    }

    @Test
    fun `course gets deadline from the first day and the lesson time`() = runBlocking {
        seriesRepo.create(series)

        val expected = Instant.parse("2099-07-01T14:00:00Z")
        assertEquals(expected, service.getSeriesDetail(series.id).getOrNull()?.series?.reservationClosesAt)
        assertEquals(expected, service.getDashboardData().getOrNull()?.series?.single()?.reservationClosesAt)
    }

    @Test
    fun `without a deadline no time is sent`() = runBlocking {
        instanceRepo.create(instance.copy(reservationDeadline = null))

        assertNull(service.getInstance(instance.id).getOrNull()?.reservationClosesAt)
    }

    @Test
    fun `client only compares the precomputed time`() {
        val closesAt = Instant.parse("2099-12-01T07:00:00Z")
        val withDeadline = instance.copy(reservationClosesAt = closesAt)

        assertFalse(withDeadline.isReservationClosed(closesAt - 1.minutes))
        assertTrue(withDeadline.isReservationClosed(closesAt))
        assertFalse(instance.isReservationClosed(closesAt), "bez uzávěrky se rezervace nezavírá")
    }

    @Test
    fun `server rejects reservation by the same deadline`() {
        val closesAt = Instant.parse("2099-12-01T07:00:00Z")

        assertFalse(instance.isReservationDeadlinePassed(closesAt - 1.minutes))
        assertTrue(instance.isReservationDeadlinePassed(closesAt))
    }
}
