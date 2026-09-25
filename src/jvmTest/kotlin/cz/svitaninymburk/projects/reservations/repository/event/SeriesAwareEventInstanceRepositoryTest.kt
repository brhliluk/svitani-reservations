package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SeriesAwareEventInstanceRepositoryTest {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val definitionId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val lessonId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")

    private val inner = InMemoryEventInstanceRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()
    private val load = InMemorySeriesLessonLoad(reservationRepo, optOutRepo)
    private val repo = SeriesAwareEventInstanceRepository(
        delegate = inner,
        load = load,
        guard = InMemorySeriesAwareCapacityGuard(inner, load),
    )

    private val lesson = EventInstance(
        id = lessonId,
        definitionId = definitionId,
        seriesId = seriesId,
        title = "Lekce",
        description = "",
        startDateTime = LocalDateTime(2026, 9, 1, 10, 0),
        endDateTime = LocalDateTime(2026, 9, 1, 11, 0),
        price = 100.0,
        capacity = 10,
        occupiedSpots = 1, // jedna přímá drop-in rezervace
    )

    private suspend fun enrolTwo() {
        reservationRepo.save(
            Reservation(
                id = Uuid.parse("00000000-0000-0000-0000-0000000000d1"),
                reference = Reference.Series(seriesId),
                contactName = "Tester",
                contactEmail = "tester@example.com",
                seatCount = 2,
                totalPrice = 100.0,
                status = Reservation.Status.CONFIRMED,
                createdAt = Clock.System.now(),
                customValues = emptyMap(),
                paymentType = PaymentType.BANK_TRANSFER,
            )
        )
    }

    @Test
    fun `read adds the series load to the stored value`() = runBlocking {
        inner.create(lesson)
        enrolTwo()

        val loaded = repo.get(lessonId)

        assertEquals(3, loaded?.occupiedSpots, "1 drop-in + 2 z kurzu")
    }

    @Test
    fun `write subtracts the derived part again`() = runBlocking {
        inner.create(lesson)
        enrolTwo()

        // Typický volající: načte instanci, něco na ní změní, uloží zpět.
        val loaded = repo.get(lessonId)!!
        repo.update(loaded.copy(isCancelled = true))

        assertEquals(
            1,
            inner.get(lessonId)?.occupiedSpots,
            "uložený sloupec smí obsahovat jen přímé rezervace, ne odvozenou zátěž kurzu",
        )
        assertEquals(3, repo.get(lessonId)?.occupiedSpots, "čtení dál vrací celek")
    }

    @Test
    fun `reservation made between read and write is not lost`() = runBlocking {
        inner.create(lesson)
        enrolTwo()

        val loaded = repo.get(lessonId)!!
        // Mezi načtením a uložením si někdo koupí drop-in.
        assertTrue(repo.attemptToReserveSpots(lessonId, 1))
        repo.update(loaded.copy(title = "Přejmenovaná"))

        assertEquals(2, inner.get(lessonId)?.occupiedSpots, "1 původní + 1 nový drop-in")
        assertEquals(4, repo.get(lessonId)?.occupiedSpots)
    }

    @Test
    fun `series load change between read and write does not corrupt the stored column`() = runBlocking {
        inner.create(lesson)
        enrolTwo()

        val loaded = repo.get(lessonId)!! // 1 přímá + 2 z kurzu
        // Mezi načtením a uložením se na kurz zapíše další člověk.
        reservationRepo.save(
            Reservation(
                id = Uuid.parse("00000000-0000-0000-0000-0000000000d2"),
                reference = Reference.Series(seriesId),
                contactName = "Druhý",
                contactEmail = "druhy@example.com",
                seatCount = 1,
                totalPrice = 100.0,
                status = Reservation.Status.CONFIRMED,
                createdAt = Clock.System.now(),
                customValues = emptyMap(),
                paymentType = PaymentType.BANK_TRANSFER,
            )
        )
        repo.update(loaded.copy(title = "Přejmenovaná"))

        assertEquals(1, inner.get(lessonId)?.occupiedSpots, "uložené jsou jen přímé rezervace")
        assertEquals(4, repo.get(lessonId)?.occupiedSpots)
    }

    @Test
    fun `isFull accounts for series participants`() = runBlocking {
        inner.create(lesson.copy(capacity = 3, occupiedSpots = 1))
        enrolTwo()

        assertTrue(repo.get(lessonId)!!.isFull, "1 + 2 = 3 z kapacity 3")
    }

    @Test
    fun `batch read enriches all items`() = runBlocking {
        inner.create(lesson)
        inner.create(
            lesson.copy(
                id = Uuid.parse("00000000-0000-0000-0000-0000000000c2"),
                occupiedSpots = 0,
            )
        )
        enrolTwo()

        val all = repo.findBySeries(seriesId)

        assertEquals(listOf(2, 3), all.map { it.occupiedSpots }.sorted())
    }
}
