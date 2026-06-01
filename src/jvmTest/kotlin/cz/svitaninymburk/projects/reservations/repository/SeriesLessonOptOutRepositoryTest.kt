package cz.svitaninymburk.projects.reservations.repository

import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedSeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReferenceDbDiscriminator
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SeriesLessonOptOutRepositoryTest {

    companion object {
        private val dbFile: File = File.createTempFile("seriesoptout-test", ".db").also { it.deleteOnExit() }

        val db: Database = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC",
        )
        val repository: ExposedSeriesLessonOptOutRepository = ExposedSeriesLessonOptOutRepository(db)

        init {
            transaction(db) {
                SchemaUtils.create(UsersTable, ReservationsTable, SeriesLessonOptOutsTable)
            }
        }
    }

    private fun insertReservation(id: Uuid = Uuid.random()): Uuid {
        transaction(db) {
            ReservationsTable.insert { row ->
                row[ReservationsTable.id] = id
                row[ReservationsTable.referenceId] = Uuid.random()
                row[ReservationsTable.referenceType] = ReferenceDbDiscriminator.SERIES
                row[ReservationsTable.registeredUserId] = null
                row[ReservationsTable.contactName] = "Test User"
                row[ReservationsTable.contactEmail] = "test@test.com"
                row[ReservationsTable.contactPhone] = null
                row[ReservationsTable.seatCount] = 1
                row[ReservationsTable.totalPrice] = 100.0
                row[ReservationsTable.paidAmount] = 0.0
                row[ReservationsTable.status] = Reservation.Status.CONFIRMED
                row[ReservationsTable.createdAt] = Clock.System.now()
                row[ReservationsTable.customValues] = emptyMap()
                row[ReservationsTable.paymentType] = PaymentInfo.Type.BANK_TRANSFER
                row[ReservationsTable.variableSymbol] = null
                row[ReservationsTable.paymentPairingToken] = null
                row[ReservationsTable.locale] = "cs"
            }
        }
        return id
    }

    @Test
    fun `save and findByReservationAndInstance returns saved opt-out`() = runBlocking {
        val reservationId = insertReservation()
        val instanceId = Uuid.random()
        val optOut = SeriesLessonOptOut(
            id = Uuid.random(),
            reservationId = reservationId,
            instanceId = instanceId,
            optedOutAt = Clock.System.now(),
            isLateCancellation = false,
        )

        repository.save(optOut)
        val found = repository.findByReservationAndInstance(reservationId, instanceId)

        assertNotNull(found)
        assertEquals(optOut.id, found.id)
        assertEquals(optOut.reservationId, found.reservationId)
        assertEquals(optOut.instanceId, found.instanceId)
        assertEquals(optOut.isLateCancellation, found.isLateCancellation)
    }

    @Test
    fun `findByReservationAndInstance returns null when not found`() = runBlocking {
        val found = repository.findByReservationAndInstance(Uuid.random(), Uuid.random())
        assertNull(found)
    }

    @Test
    fun `findByReservation returns all opt-outs for a reservation`() = runBlocking {
        val reservationId = insertReservation()
        val instanceId1 = Uuid.random()
        val instanceId2 = Uuid.random()
        val now = Clock.System.now()

        repository.save(SeriesLessonOptOut(Uuid.random(), reservationId, instanceId1, now, false))
        repository.save(SeriesLessonOptOut(Uuid.random(), reservationId, instanceId2, now, true))

        val otherReservationId = insertReservation()
        repository.save(SeriesLessonOptOut(Uuid.random(), otherReservationId, Uuid.random(), now, false))

        val results = repository.findByReservation(reservationId)
        assertEquals(2, results.size)
        val instanceIds = results.map { it.instanceId }.toSet()
        assert(instanceId1 in instanceIds)
        assert(instanceId2 in instanceIds)
    }
}
