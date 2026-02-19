package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.util.dbQuery
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.json.json
import kotlin.uuid.Uuid

enum class ReferenceDbDiscriminator { INSTANCE, SERIES }

object ReservationsTable : Table("reservations") {
    val id = uuid("id").autoGenerate()

    val referenceId = uuid("reference_id")
    val referenceType = enumerationByName("reference_type", 20, ReferenceDbDiscriminator::class)

    val registeredUserId = optReference(
        name = "registered_user_id",
        refColumn = UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val contactName = varchar("contact_name", 255)
    val contactEmail = varchar("contact_email", 255)
    val contactPhone = varchar("contact_phone", 50).nullable()
    val userId = optReference(
        name = "user_id",
        refColumn = UsersTable.id,
        onDelete = ReferenceOption.CASCADE,
    )

    val seatCount = integer("seat_count").default(1)
    val totalPrice = double("total_price")
    val paidAmount = double("paid_amount").default(0.0)

    val status = enumerationByName("status", 30, Reservation.Status::class)
    val createdAt = timestamp("created_at")

    val customValues = json<Map<String, CustomFieldValue>>("custom_values", Json)

    val paymentType = enumerationByName("payment_type", 30, PaymentInfo.Type::class)
    val variableSymbol = varchar("variable_symbol", 50).nullable()
    val paymentPairingToken = varchar("payment_pairing_token", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

class ExposedReservationRepository : ReservationRepository {
    override suspend fun save(reservation: Reservation): Reservation = dbQuery {
        val updatedRows = ReservationsTable.update({ ReservationsTable.id eq reservation.id }) { row ->
            row[registeredUserId] = reservation.registeredUserId
            row[contactName] = reservation.contactName
            row[contactEmail] = reservation.contactEmail
            row[contactPhone] = reservation.contactPhone
            row[userId] = reservation.userId
            row[seatCount] = reservation.seatCount
            row[totalPrice] = reservation.totalPrice
            row[paidAmount] = reservation.paidAmount
            row[status] = reservation.status
            row[paymentType] = reservation.paymentType
            row[variableSymbol] = reservation.variableSymbol
            row[paymentPairingToken] = reservation.paymentPairingToken

            row[customValues] = reservation.customValues
        }

        if (updatedRows == 0) {
            ReservationsTable.insert { row ->
                row[id] = reservation.id

                when (reservation.reference) {
                    is Reference.Instance -> {
                        row[referenceId] = reservation.reference.id
                        row[referenceType] = ReferenceDbDiscriminator.INSTANCE
                    }
                    is Reference.Series -> {
                        row[referenceId] = reservation.reference.id
                        row[referenceType] = ReferenceDbDiscriminator.SERIES
                    }
                }

                row[registeredUserId] = reservation.registeredUserId
                row[contactName] = reservation.contactName
                row[contactEmail] = reservation.contactEmail
                row[contactPhone] = reservation.contactPhone
                row[userId] = reservation.userId
                row[seatCount] = reservation.seatCount
                row[totalPrice] = reservation.totalPrice
                row[paidAmount] = reservation.paidAmount
                row[status] = reservation.status
                row[createdAt] = reservation.createdAt
                row[customValues] = reservation.customValues
                row[paymentType] = reservation.paymentType
                row[variableSymbol] = reservation.variableSymbol
                row[paymentPairingToken] = reservation.paymentPairingToken
            }
        }
        reservation
    }

    override suspend fun findById(id: Uuid): Reservation? = dbQuery {
        ReservationsTable.selectAll()
            .where { ReservationsTable.id eq id }
            .map { it.toReservation() }
            .singleOrNull()
    }

    override suspend fun findAwaitingPayment(vs: String): Reservation? = dbQuery {
        ReservationsTable.selectAll()
            .where {
                (ReservationsTable.variableSymbol eq vs) and (ReservationsTable.status eq Reservation.Status.PENDING_PAYMENT)
            }
            .map { it.toReservation() }
            .singleOrNull()
    }

    override suspend fun hasPendingReservations(): Boolean = dbQuery {
        !ReservationsTable.selectAll()
            .where { ReservationsTable.status eq Reservation.Status.PENDING_PAYMENT }
            .empty()
    }

    override suspend fun countSeats(id: Uuid): Int = dbQuery {
        val sumColumn = ReservationsTable.seatCount.sum()

        val result = ReservationsTable.select(sumColumn)
            .where {
                (ReservationsTable.referenceId eq id) and
                        (ReservationsTable.status neq Reservation.Status.CANCELLED) and
                        (ReservationsTable.status neq Reservation.Status.REJECTED)
            }
            .firstOrNull()

        result?.getOrNull(sumColumn) ?: 0
    }

    override suspend fun getAll(userId: Uuid): List<Reservation> = dbQuery {
        ReservationsTable.selectAll()
            .where { ReservationsTable.userId eq userId }
            .map { it.toReservation() }
    }

    override suspend fun existsByVariableSymbol(variableSymbol: String): Boolean = dbQuery {
        !ReservationsTable.selectAll()
            .where { ReservationsTable.variableSymbol eq variableSymbol }
            .empty()
    }
}

fun ResultRow.toReservation(): Reservation {
    val refId = this[ReservationsTable.referenceId]
    val reference = when (this[ReservationsTable.referenceType]) {
        ReferenceDbDiscriminator.INSTANCE -> Reference.Instance(refId)
        ReferenceDbDiscriminator.SERIES -> Reference.Series(refId)
    }

    return Reservation(
        id = this[ReservationsTable.id],
        reference = reference,
        registeredUserId = this[ReservationsTable.registeredUserId],
        contactName = this[ReservationsTable.contactName],
        contactEmail = this[ReservationsTable.contactEmail],
        contactPhone = this[ReservationsTable.contactPhone],
        userId = this[ReservationsTable.userId],
        seatCount = this[ReservationsTable.seatCount],
        totalPrice = this[ReservationsTable.totalPrice],
        paidAmount = this[ReservationsTable.paidAmount],
        status = this[ReservationsTable.status],
        createdAt = this[ReservationsTable.createdAt],

        customValues = this[ReservationsTable.customValues],

        paymentType = this[ReservationsTable.paymentType],
        variableSymbol = this[ReservationsTable.variableSymbol],
        paymentPairingToken = this[ReservationsTable.paymentPairingToken]
    )
}