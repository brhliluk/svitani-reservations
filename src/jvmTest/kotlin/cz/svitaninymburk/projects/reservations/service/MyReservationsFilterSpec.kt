package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Co se objeví v „Moje rezervace“. Drží to stejné pravidlo jako přivlastnění
 * ([isStillRunning]) — kdyby se rozešla, tlačítko „Přidat do mých rezervací“ by
 * připsalo rezervaci, která se v seznamu neukáže.
 */
class MyReservationsFilterSpec {

    private val userId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000b1")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()

    private val service = AuthenticatedReservationService(instanceRepo, seriesRepo, reservationRepo)

    private suspend fun givenInstance(year: Int, cancelled: Boolean = false): EventInstance =
        instanceRepo.create(
            EventInstance(
                id = Uuid.random(),
                definitionId = Uuid.random(),
                title = "Akce $year",
                description = "",
                startDateTime = LocalDateTime(year, 6, 1, 10, 0),
                endDateTime = LocalDateTime(year, 6, 1, 11, 0),
                price = 100.0,
                capacity = 10,
                occupiedSpots = 1,
                isCancelled = cancelled,
            )
        )

    private suspend fun givenSeries(endYear: Int, cancelled: Boolean = false): EventSeries =
        seriesRepo.create(
            EventSeries(
                id = Uuid.random(),
                definitionId = Uuid.random(),
                title = "Kurz do $endYear",
                description = "",
                price = 500.0,
                capacity = 10,
                occupiedSpots = 1,
                startDate = LocalDate(2020, 1, 1),
                endDate = LocalDate(endYear, 12, 31),
                lessonCount = 10,
                isCancelled = cancelled,
            )
        )

    private suspend fun givenReservation(
        reference: Reference,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.random(),
            reference = reference,
            registeredUserId = userId,
            contactName = "Jan Host",
            contactEmail = "host@test.cz",
            seatCount = 1,
            totalPrice = 500.0,
            status = status,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.BANK_TRANSFER,
        )
    )

    private suspend fun listedIds() = service.getReservations(userId).getOrNull()!!.map { it.id }

    @Test
    fun `budouci akce se vraci`() = runBlocking {
        val reservation = givenReservation(Reference.Instance(givenInstance(2099).id))
        assertEquals(listOf(reservation.id), listedIds())
        Unit
    }

    @Test
    fun `probehla akce se nevraci`() = runBlocking {
        givenReservation(Reference.Instance(givenInstance(2020).id))
        assertTrue(listedIds().isEmpty())
        Unit
    }

    @Test
    fun `zrusena akce se nevraci`() = runBlocking {
        givenReservation(Reference.Instance(givenInstance(2099, cancelled = true).id))
        assertTrue(listedIds().isEmpty())
        Unit
    }

    @Test
    fun `zrusena rezervace se nevraci`() = runBlocking {
        givenReservation(
            Reference.Instance(givenInstance(2099).id),
            status = Reservation.Status.CANCELLED,
        )
        assertTrue(listedIds().isEmpty())
        Unit
    }

    /** Kurz, kterému část lekcí už proběhla, pořád běží — rozhoduje jeho konec. */
    @Test
    fun `kurz s casti lekci v minulosti se vraci`() = runBlocking {
        val reservation = givenReservation(Reference.Series(givenSeries(endYear = 2099).id))
        assertEquals(listOf(reservation.id), listedIds())
        Unit
    }

    @Test
    fun `dobehnuty kurz se nevraci`() = runBlocking {
        givenReservation(Reference.Series(givenSeries(endYear = 2020).id))
        assertTrue(listedIds().isEmpty())
        Unit
    }

    /**
     * Dřív se `EventSeries.isCancelled` ignorovalo a zrušený kurz v seznamu zůstával —
     * sjednocení pravidla s přivlastněním to zároveň spravilo.
     */
    @Test
    fun `zruseny kurz se nevraci`() = runBlocking {
        givenReservation(Reference.Series(givenSeries(endYear = 2099, cancelled = true).id))
        assertTrue(listedIds().isEmpty())
        Unit
    }

    @Test
    fun `cizi rezervace se nevraci`() = runBlocking {
        reservationRepo.save(
            Reservation(
                id = Uuid.random(),
                reference = Reference.Instance(givenInstance(2099).id),
                registeredUserId = Uuid.random(),
                contactName = "Nekdo Jiny",
                contactEmail = "jiny@test.cz",
                seatCount = 1,
                totalPrice = 100.0,
                status = Reservation.Status.CONFIRMED,
                createdAt = Clock.System.now(),
                customValues = emptyMap(),
                paymentType = PaymentInfo.Type.BANK_TRANSFER,
            )
        )
        assertTrue(listedIds().isEmpty())
        Unit
    }
}
