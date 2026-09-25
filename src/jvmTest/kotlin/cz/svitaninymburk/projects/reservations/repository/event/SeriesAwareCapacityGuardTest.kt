package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SeriesAwareCapacityGuardTest {

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

    /** Drop-in lekce s kapacitou 2, zatím bez jediné přímé rezervace. */
    private val lesson = EventInstance(
        id = lessonId,
        definitionId = definitionId,
        seriesId = seriesId,
        title = "Lekce",
        description = "",
        startDateTime = LocalDateTime(2026, 9, 1, 10, 0),
        endDateTime = LocalDateTime(2026, 9, 1, 11, 0),
        price = 100.0,
        capacity = 2,
        isDropIn = true,
    )

    private suspend fun enrol(id: Uuid, seats: Int): Reservation = reservationRepo.save(
        Reservation(
            id = id,
            reference = Reference.Series(seriesId),
            contactName = "Tester",
            contactEmail = "tester@example.com",
            seatCount = seats,
            totalPrice = 100.0,
            status = Reservation.Status.CONFIRMED,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentType.BANK_TRANSFER,
        )
    )

    @Test
    fun `full series blocks drop-in reservation`() = runBlocking {
        inner.create(lesson)
        enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d1"), seats = 2)

        assertFalse(repo.attemptToReserveSpots(lessonId, 1), "kapacita 2, kurz drží 2 místa")
    }

    @Test
    fun `lesson opt-out frees the seats`() = runBlocking {
        inner.create(lesson)
        val reservation = enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d1"), seats = 2)
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(),
                reservationId = reservation.id,
                instanceId = lessonId,
                optedOutAt = Clock.System.now(),
                isLateCancellation = false,
            )
        )

        assertTrue(repo.attemptToReserveSpots(lessonId, 2), "obě místa se omluvou uvolnila")
        assertFalse(repo.attemptToReserveSpots(lessonId, 1), "a víc už se jich tam nevejde")
    }

    @Test
    fun `partially filled series leaves the remaining capacity`() = runBlocking {
        inner.create(lesson)
        enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d1"), seats = 1)

        assertTrue(repo.attemptToReserveSpots(lessonId, 1), "kapacita 2, kurz drží 1")
        assertFalse(repo.attemptToReserveSpots(lessonId, 1), "teď už je plno")
    }

    @Test
    fun `lesson without a series works as before`() = runBlocking {
        val standalone = lesson.copy(
            id = Uuid.parse("00000000-0000-0000-0000-0000000000c9"),
            seriesId = null,
        )
        inner.create(standalone)

        assertTrue(repo.attemptToReserveSpots(standalone.id, 2))
        assertFalse(repo.attemptToReserveSpots(standalone.id, 1))
    }
}
