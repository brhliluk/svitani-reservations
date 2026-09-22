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
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SeriesLessonLoadTest {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val otherSeriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a2")
    private val definitionId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")

    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()
    private val load = InMemorySeriesLessonLoad(reservationRepo, optOutRepo)

    private fun lesson(id: Uuid, series: Uuid? = seriesId) = EventInstance(
        id = id,
        definitionId = definitionId,
        seriesId = series,
        title = "Lekce",
        description = "",
        startDateTime = LocalDateTime(2026, 9, 1, 10, 0),
        endDateTime = LocalDateTime(2026, 9, 1, 11, 0),
        price = 100.0,
        capacity = 10,
    )

    private suspend fun enrol(
        id: Uuid,
        seats: Int,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
        series: Uuid = seriesId,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = id,
            reference = Reference.Series(series),
            contactName = "Tester",
            contactEmail = "tester@example.com",
            seatCount = seats,
            totalPrice = 100.0,
            status = status,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentType.BANK_TRANSFER,
        )
    )

    private suspend fun optOut(reservation: Reservation, instanceId: Uuid) = optOutRepo.save(
        SeriesLessonOptOut(
            id = Uuid.random(),
            reservationId = reservation.id,
            instanceId = instanceId,
            optedOutAt = Clock.System.now(),
            isLateCancellation = false,
        )
    )

    @Test
    fun `aktivni prihlasky na kurz zatezuji kazdou lekci`() = runBlocking {
        val lessonA = lesson(Uuid.parse("00000000-0000-0000-0000-0000000000c1"))
        val lessonB = lesson(Uuid.parse("00000000-0000-0000-0000-0000000000c2"))
        enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d1"), seats = 2)
        enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d2"), seats = 1)

        val result = load.forInstances(listOf(lessonA, lessonB))

        assertEquals(3, result[lessonA.id])
        assertEquals(3, result[lessonB.id])
    }

    @Test
    fun `omluvenka odecte vsechna mista sve rezervace`() = runBlocking {
        val lessonA = lesson(Uuid.parse("00000000-0000-0000-0000-0000000000c1"))
        val lessonB = lesson(Uuid.parse("00000000-0000-0000-0000-0000000000c2"))
        val rodina = enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d1"), seats = 2)
        enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d2"), seats = 1)
        optOut(rodina, lessonA.id)

        val result = load.forInstances(listOf(lessonA, lessonB))

        assertEquals(1, result[lessonA.id], "rodina se dvěma místy se z lekce A omluvila")
        assertEquals(3, result[lessonB.id], "lekce B zůstává nedotčená")
    }

    @Test
    fun `zrusena rezervace s omluvenkou se neodecte dvakrat`() = runBlocking {
        val lessonA = lesson(Uuid.parse("00000000-0000-0000-0000-0000000000c1"))
        val zrusena = enrol(
            Uuid.parse("00000000-0000-0000-0000-0000000000d1"),
            seats = 2,
            status = Reservation.Status.CONFIRMED,
        )
        optOut(zrusena, lessonA.id)
        enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d2"), seats = 3)
        reservationRepo.updateStatus(zrusena.id, Reservation.Status.CANCELLED)

        val result = load.forInstances(listOf(lessonA))

        assertEquals(3, result[lessonA.id], "zrušená rezervace už není v součtu, omluvenka ji nesmí odečíst znovu")
    }

    @Test
    fun `cekatel z poradniku misto nedrzi`() = runBlocking {
        val lessonA = lesson(Uuid.parse("00000000-0000-0000-0000-0000000000c1"))
        enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d1"), seats = 1)
        enrol(
            Uuid.parse("00000000-0000-0000-0000-0000000000d2"),
            seats = 4,
            status = Reservation.Status.WAITLISTED,
        )

        val result = load.forInstances(listOf(lessonA))

        assertEquals(1, result[lessonA.id])
    }

    @Test
    fun `prihlaska na jinou serii lekci nezatezuje`() = runBlocking {
        val lessonA = lesson(Uuid.parse("00000000-0000-0000-0000-0000000000c1"))
        enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d1"), seats = 5, series = otherSeriesId)

        val result = load.forInstances(listOf(lessonA))

        assertEquals(0, result[lessonA.id])
    }

    @Test
    fun `lekce bez serie ma nulovou zatez`() = runBlocking {
        val samostatna = lesson(Uuid.parse("00000000-0000-0000-0000-0000000000c9"), series = null)
        enrol(Uuid.parse("00000000-0000-0000-0000-0000000000d1"), seats = 3)

        val result = load.forInstances(listOf(samostatna))

        assertEquals(0, result[samostatna.id])
    }
}
