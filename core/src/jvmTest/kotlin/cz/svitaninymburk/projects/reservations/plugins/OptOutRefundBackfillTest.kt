package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.repository.reservation.ReferenceDbDiscriminator
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.repository.wallet.WalletTransactionsTable
import cz.svitaninymburk.projects.reservations.repository.wallet.WalletsTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Historické omluvenky nemají uloženo, kolik za ně odešlo. Backfill to dohledá
 * podle času transakce — a nesmí přitom omluvence přiřknout kredit za lekci
 * zrušenou adminem, který jde pod stejným důvodem.
 */
class OptOutRefundBackfillTest {

    private val reservationId = Uuid.random()
    private val walletId = Uuid.random()
    private val t0 = Instant.parse("2026-09-12T10:00:00Z")

    private fun newDb(): Database {
        val dbFile = File.createTempFile("opt-out-refund-backfill-test", ".db").also { it.deleteOnExit() }
        return Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
    }

    private fun setUp(db: Database) = transaction(db) {
        SchemaUtils.create(UsersTable, ReservationsTable, SeriesLessonOptOutsTable, WalletsTable, WalletTransactionsTable)
        ReservationsTable.insert {
            it[id] = reservationId
            it[referenceId] = Uuid.random()
            it[referenceType] = ReferenceDbDiscriminator.SERIES
            it[contactName] = "Jana"
            it[contactEmail] = "jana@test.com"
            it[seatCount] = 1
            it[totalPrice] = 1000.0
            it[paidAmount] = 1000.0
            it[status] = Reservation.Status.CONFIRMED
            it[createdAt] = Clock.System.now()
            it[customValues] = emptyMap<String, CustomFieldValue>()
            it[paymentType] = PaymentType.BANK_TRANSFER
        }
        WalletsTable.insert {
            it[id] = walletId
            it[code] = "SVIT-TEST-0001"
            it[ownerEmail] = "jana@test.com"
            it[balance] = 0.0
            it[createdAt] = t0
        }
    }

    private fun optOut(at: Instant, isLate: Boolean = false): Uuid {
        val id = Uuid.random()
        SeriesLessonOptOutsTable.insert {
            it[SeriesLessonOptOutsTable.id] = id
            it[reservationId] = this@OptOutRefundBackfillTest.reservationId
            it[instanceId] = Uuid.random()
            it[optedOutAt] = at
            it[isLateCancellation] = isLate
            it[refundedAmount] = null
        }
        return id
    }

    private fun credit(at: Instant, amount: Double) {
        WalletTransactionsTable.insert {
            it[walletId] = this@OptOutRefundBackfillTest.walletId
            it[WalletTransactionsTable.amount] = amount
            it[reason] = WalletTransactionReason.LESSON_OPT_OUT_REFUND
            it[reservationId] = this@OptOutRefundBackfillTest.reservationId
            it[createdAt] = at
        }
    }

    private fun refunded(id: Uuid): Double? =
        SeriesLessonOptOutsTable.selectAll().single { it[SeriesLessonOptOutsTable.id] == id }[SeriesLessonOptOutsTable.refundedAmount]

    @Test
    fun `amount is matched by time and a credit for a cancelled lesson is not attributed`() {
        val db = newDb()
        setUp(db)
        val (refundedOptOut, unpaidOptOut, lateOptOut) = transaction(db) {
            val refundedOptOut = optOut(t0)
            credit(t0 + 15.milliseconds, 150.0)
            // Omluvenka z doby, kdy kurz ještě nebyl zaplacený — žádná transakce.
            val unpaidOptOut = optOut(t0 + 1.days)
            // Admin o dva dny později zrušil jinou lekci — tentýž důvod transakce.
            credit(t0 + 2.days, 150.0)
            val lateOptOut = optOut(t0 + 3.days, isLate = true)
            Triple(refundedOptOut, unpaidOptOut, lateOptOut)
        }

        transaction(db) { assertEquals(3, backfillOptOutRefundedAmounts()) }

        transaction(db) {
            assertEquals(150.0, refunded(refundedOptOut))
            assertEquals(0.0, refunded(unpaidOptOut), "kredit za zrušenou lekci jí nepatří")
            assertEquals(0.0, refunded(lateOptOut))
        }
    }

    @Test
    fun `two opt-outs in quick succession do not both claim one transaction`() {
        val db = newDb()
        setUp(db)
        val (firstOptOut, secondOptOut) = transaction(db) {
            val firstOptOut = optOut(t0)
            val secondOptOut = optOut(t0 + 5.milliseconds)
            credit(t0 + 10.milliseconds, 150.0)
            firstOptOut to secondOptOut
        }

        transaction(db) { backfillOptOutRefundedAmounts() }

        transaction(db) {
            assertEquals(150.0, (refunded(firstOptOut) ?: 0.0) + (refunded(secondOptOut) ?: 0.0))
        }
    }

    @Test
    fun `second run does nothing`() {
        val db = newDb()
        setUp(db)
        transaction(db) { optOut(t0) }

        transaction(db) { assertEquals(1, backfillOptOutRefundedAmounts()) }
        transaction(db) { assertEquals(0, backfillOptOutRefundedAmounts()) }
    }
}
