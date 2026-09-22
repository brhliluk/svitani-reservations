package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedSeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * [SeriesAwareCapacityGuardTest] pokrývá jen in-memory variantu. Ostrý guard je
 * ale jediný SQL příkaz s dvěma skalárními poddotazy, ručně skládaným COALESCE
 * a ořezem přes CASE WHEN — konstrukce, která nikde jinde v projektu není. Chyba v ní by neshodila
 * kompilaci, jen by za běhu buď spadla, nebo tiše počítala špatně, takže tenhle
 * test jede přes reálnou SQLite a ověřuje stejné scénáře skrz
 * [ExposedSeriesAwareCapacityGuard].
 */
class ExposedSeriesAwareCapacityGuardTest {

    companion object {
        private val dbFile: File = File.createTempFile("capacity-guard-test", ".db").also { it.deleteOnExit() }

        val db: Database = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC",
        )

        init {
            transaction(db) {
                SchemaUtils.create(
                    UsersTable,
                    ReservationsTable,
                    SeriesLessonOptOutsTable,
                    EventDefinitionsTable,
                    EventSeriesTable,
                    EventInstancesTable,
                    EventOwnerEmailsTable,
                )
            }
        }
    }

    private val definitionId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val instanceRepo = ExposedEventInstanceRepository()
    private val reservationRepo = ExposedReservationRepository()
    private val optOutRepo = ExposedSeriesLessonOptOutRepository()
    private val guard = ExposedSeriesAwareCapacityGuard()

    /** Drop-in lekce s kapacitou 2, zatím bez jediné přímé rezervace. */
    private suspend fun lesson(series: Uuid, capacity: Int = 2): EventInstance = instanceRepo.create(
        EventInstance(
            id = Uuid.random(),
            definitionId = definitionId,
            seriesId = series,
            title = "Lekce",
            description = "",
            startDateTime = LocalDateTime(2026, 9, 1, 10, 0),
            endDateTime = LocalDateTime(2026, 9, 1, 11, 0),
            price = 100.0,
            capacity = capacity,
            isDropIn = true,
        )
    )

    private suspend fun enrol(
        series: Uuid,
        seats: Int,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.random(),
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
    fun `plny kurz nedovoli drop-in rezervaci`() = runBlocking {
        val seriesId = Uuid.random()
        val lekce = lesson(seriesId)
        enrol(seriesId, seats = 2)

        assertFalse(guard.attemptToReserveSpots(lekce.id, seriesId, 1), "kapacita 2, kurz drží 2 místa")
        assertEquals(0, instanceRepo.get(lekce.id)?.occupiedSpots, "odmítnutý pokus nesmí nic zapsat")
    }

    @Test
    fun `omluva z lekce misto uvolni`() = runBlocking {
        val seriesId = Uuid.random()
        val lekce = lesson(seriesId)
        val rezervace = enrol(seriesId, seats = 2)
        optOut(rezervace, lekce.id)

        assertTrue(guard.attemptToReserveSpots(lekce.id, seriesId, 2), "obě místa se omluvou uvolnila")
        assertFalse(guard.attemptToReserveSpots(lekce.id, seriesId, 1), "a víc už se jich tam nevejde")
        assertEquals(2, instanceRepo.get(lekce.id)?.occupiedSpots)
    }

    @Test
    fun `castecne obsazeny kurz nechava zbytek kapacity`() = runBlocking {
        val seriesId = Uuid.random()
        val lekce = lesson(seriesId)
        enrol(seriesId, seats = 1)

        assertTrue(guard.attemptToReserveSpots(lekce.id, seriesId, 1), "kapacita 2, kurz drží 1")
        assertFalse(guard.attemptToReserveSpots(lekce.id, seriesId, 1), "teď už je plno")
    }

    @Test
    fun `zrusena prihlaska misto nedrzi`() = runBlocking {
        val seriesId = Uuid.random()
        val lekce = lesson(seriesId)
        enrol(seriesId, seats = 2, status = Reservation.Status.CANCELLED)

        assertTrue(guard.attemptToReserveSpots(lekce.id, seriesId, 2), "zrušená přihláška se do kapacity nepočítá")
    }

    @Test
    fun `zaporna zatez z rozbitych dat kapacitu nerozsiri`() = runBlocking {
        val seriesId = Uuid.random()
        val lekce = lesson(seriesId)
        // Rozbitý stav: omluvenka na lekci téhle série, ale od přihlášky na sérii jinou.
        // Přes službu nevznikne (kontroluje shodu sérií), přímým zápisem ano — a bez ořezu
        // na nulu by záporná zátěž kapacitu naopak rozšířila.
        val cizi = enrol(Uuid.random(), seats = 2)
        optOut(cizi, lekce.id)

        assertFalse(guard.attemptToReserveSpots(lekce.id, seriesId, 3), "kapacita je 2, ne 4")
        assertTrue(guard.attemptToReserveSpots(lekce.id, seriesId, 2), "celá kapacita 2 zůstává k dispozici")
        assertEquals(2, instanceRepo.get(lekce.id)?.occupiedSpots)
    }

    @Test
    fun `prihlaska na jinou serii kapacitu neukrajuje`() = runBlocking {
        val seriesId = Uuid.random()
        val lekce = lesson(seriesId)
        enrol(Uuid.random(), seats = 5)

        assertTrue(guard.attemptToReserveSpots(lekce.id, seriesId, 2))
    }
}
