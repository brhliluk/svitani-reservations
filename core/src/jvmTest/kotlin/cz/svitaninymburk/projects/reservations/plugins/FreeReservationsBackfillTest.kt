package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.repository.reservation.ReferenceDbDiscriminator
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Rezervace na akce zdarma, které vznikly ještě dřív, než je server začal
 * zakládat rovnou potvrzené, zůstaly v DB jako PENDING_PAYMENT — a držely tím
 * naživo dotazování FIO i admin přehled nezaplacených.
 */
class FreeReservationsBackfillTest {

    private fun newDb(): Database {
        val dbFile = File.createTempFile("free-reservations-backfill-test", ".db").also { it.deleteOnExit() }
        return Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
    }

    private fun insertReservation(
        id: Uuid,
        totalPrice: Double,
        status: Reservation.Status,
        paymentType: PaymentType,
        paidAmount: Double = 0.0,
    ) {
        ReservationsTable.insert {
            it[ReservationsTable.id] = id
            it[referenceId] = Uuid.random()
            it[referenceType] = ReferenceDbDiscriminator.INSTANCE
            it[contactName] = "Jan Novak"
            it[contactEmail] = "jan@test.com"
            it[seatCount] = 1
            it[ReservationsTable.totalPrice] = totalPrice
            it[ReservationsTable.paidAmount] = paidAmount
            it[ReservationsTable.status] = status
            it[createdAt] = Clock.System.now()
            it[customValues] = emptyMap<String, CustomFieldValue>()
            it[ReservationsTable.paymentType] = paymentType
        }
    }

    private fun reservation(id: Uuid) =
        ReservationsTable.selectAll().single { it[ReservationsTable.id] == id }

    @Test
    fun `fresh database - backfill is a no-op and does not throw`() {
        val db = newDb()
        transaction(db) {
            SchemaUtils.create(ReservationsTable)
            assertEquals(0, confirmFreeReservations())
        }
    }

    @Test
    fun `pending reservation with zero price becomes confirmed and free`() {
        val db = newDb()
        val id = Uuid.random()

        transaction(db) {
            SchemaUtils.create(ReservationsTable)
            insertReservation(id, totalPrice = 0.0, status = Reservation.Status.PENDING_PAYMENT, paymentType = PaymentType.BANK_TRANSFER)
        }

        transaction(db) { assertEquals(1, confirmFreeReservations()) }

        transaction(db) {
            val row = reservation(id)
            assertEquals(Reservation.Status.CONFIRMED, row[ReservationsTable.status])
            assertEquals(PaymentType.FREE, row[ReservationsTable.paymentType])
            assertEquals(0.0, row[ReservationsTable.paidAmount])
        }
    }

    @Test
    fun `pending reservation with a price is left untouched`() {
        val db = newDb()
        val id = Uuid.random()

        transaction(db) {
            SchemaUtils.create(ReservationsTable)
            insertReservation(id, totalPrice = 150.0, status = Reservation.Status.PENDING_PAYMENT, paymentType = PaymentType.BANK_TRANSFER)
        }

        transaction(db) { assertEquals(0, confirmFreeReservations()) }

        transaction(db) {
            val row = reservation(id)
            assertEquals(Reservation.Status.PENDING_PAYMENT, row[ReservationsTable.status])
            assertEquals(PaymentType.BANK_TRANSFER, row[ReservationsTable.paymentType])
        }
    }

    @Test
    fun `confirmed paid reservation is left untouched`() {
        val db = newDb()
        val id = Uuid.random()

        transaction(db) {
            SchemaUtils.create(ReservationsTable)
            insertReservation(
                id,
                totalPrice = 300.0,
                status = Reservation.Status.CONFIRMED,
                paymentType = PaymentType.BANK_TRANSFER,
                paidAmount = 300.0,
            )
        }

        transaction(db) { assertEquals(0, confirmFreeReservations()) }

        transaction(db) {
            val row = reservation(id)
            assertEquals(Reservation.Status.CONFIRMED, row[ReservationsTable.status])
            assertEquals(PaymentType.BANK_TRANSFER, row[ReservationsTable.paymentType])
            assertEquals(300.0, row[ReservationsTable.paidAmount])
        }
    }

    @Test
    fun `waitlisted zero price reservation stays in the waitlist`() {
        val db = newDb()
        val id = Uuid.random()

        transaction(db) {
            SchemaUtils.create(ReservationsTable)
            insertReservation(id, totalPrice = 0.0, status = Reservation.Status.WAITLISTED, paymentType = PaymentType.FREE)
        }

        transaction(db) { assertEquals(0, confirmFreeReservations()) }

        transaction(db) {
            assertEquals(Reservation.Status.WAITLISTED, reservation(id)[ReservationsTable.status])
        }
    }

    @Test
    fun `cancelled zero price reservation stays cancelled`() {
        val db = newDb()
        val id = Uuid.random()

        transaction(db) {
            SchemaUtils.create(ReservationsTable)
            insertReservation(id, totalPrice = 0.0, status = Reservation.Status.CANCELLED, paymentType = PaymentType.FREE)
        }

        transaction(db) { assertEquals(0, confirmFreeReservations()) }

        transaction(db) {
            assertEquals(Reservation.Status.CANCELLED, reservation(id)[ReservationsTable.status])
        }
    }

    @Test
    fun `second run changes nothing`() {
        val db = newDb()
        val id = Uuid.random()

        transaction(db) {
            SchemaUtils.create(ReservationsTable)
            insertReservation(id, totalPrice = 0.0, status = Reservation.Status.PENDING_PAYMENT, paymentType = PaymentType.BANK_TRANSFER)
        }

        transaction(db) { confirmFreeReservations() }
        transaction(db) { assertEquals(0, confirmFreeReservations()) }

        transaction(db) {
            val row = reservation(id)
            assertEquals(Reservation.Status.CONFIRMED, row[ReservationsTable.status])
            assertEquals(PaymentType.FREE, row[ReservationsTable.paymentType])
        }
    }
}
