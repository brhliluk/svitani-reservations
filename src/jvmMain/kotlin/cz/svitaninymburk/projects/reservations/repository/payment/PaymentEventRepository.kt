package cz.svitaninymburk.projects.reservations.repository.payment

import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.util.dbQuery
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock
import kotlin.uuid.Uuid

object PaymentEventsTable : Table("payment_events") {
    val id = uuid("id").autoGenerate()
    val reservationId = reference("reservation_id", ReservationsTable.id, onDelete = ReferenceOption.CASCADE)
    val amount = double("amount")
    val currency = varchar("currency", 10).default("CZK")
    val type = enumerationByName("type", 30, PaymentInfo.Type::class)
    val paymentSource = enumerationByName("source", 30, PaymentEvent.Source::class)
    val processedAt = timestamp("processed_at")
    override val primaryKey = PrimaryKey(id)
}

interface PaymentEventRepository {
    suspend fun insert(event: NewPaymentEvent)
    suspend fun findAll(page: Int, pageSize: Int): List<PaymentEvent>
    suspend fun countAll(): Long
}

data class NewPaymentEvent(
    val reservationId: Uuid,
    val amount: Double,
    val currency: String = "CZK",
    val type: PaymentInfo.Type,
    val source: PaymentEvent.Source,
)

class ExposedPaymentEventRepository : PaymentEventRepository {

    override suspend fun insert(event: NewPaymentEvent): Unit = dbQuery {
        PaymentEventsTable.insert {
            it[reservationId] = event.reservationId
            it[amount] = event.amount
            it[currency] = event.currency
            it[type] = event.type
            it[paymentSource] = event.source
            it[processedAt] = Clock.System.now()
        }
    }

    override suspend fun findAll(page: Int, pageSize: Int): List<PaymentEvent> = dbQuery {
        PaymentEventsTable
            .join(ReservationsTable, JoinType.LEFT,
                onColumn = PaymentEventsTable.reservationId,
                otherColumn = ReservationsTable.id)
            .selectAll()
            .orderBy(PaymentEventsTable.processedAt, SortOrder.DESC)
            .limit(pageSize)
            .offset(page.toLong() * pageSize)
            .map { row ->
                PaymentEvent(
                    id = row[PaymentEventsTable.id].toString(),
                    reservationId = row[PaymentEventsTable.reservationId].toString(),
                    contactName = row.getOrNull(ReservationsTable.contactName) ?: "",
                    amount = row[PaymentEventsTable.amount],
                    currency = row[PaymentEventsTable.currency],
                    type = row[PaymentEventsTable.type],
                    source = row[PaymentEventsTable.paymentSource],
                    processedAt = row[PaymentEventsTable.processedAt],
                )
            }
    }

    override suspend fun countAll(): Long = dbQuery {
        PaymentEventsTable
            .join(ReservationsTable, JoinType.LEFT,
                onColumn = PaymentEventsTable.reservationId,
                otherColumn = ReservationsTable.id)
            .selectAll()
            .count()
    }
}
