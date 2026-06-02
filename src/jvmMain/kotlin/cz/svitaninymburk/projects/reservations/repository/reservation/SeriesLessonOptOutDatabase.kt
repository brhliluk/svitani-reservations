package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.uuid.Uuid

object SeriesLessonOptOutsTable : Table("series_lesson_opt_outs") {
    val id = uuid("id")
    val reservationId = uuid("reservation_id").references(ReservationsTable.id, onDelete = ReferenceOption.CASCADE)
    val instanceId = uuid("instance_id")
    val optedOutAt = timestamp("opted_out_at")
    val isLateCancellation = bool("is_late_cancellation")
    override val primaryKey = PrimaryKey(id)
}

interface SeriesLessonOptOutRepository {
    suspend fun save(optOut: SeriesLessonOptOut): SeriesLessonOptOut
    suspend fun findByReservationAndInstance(reservationId: Uuid, instanceId: Uuid): SeriesLessonOptOut?
    suspend fun findByReservation(reservationId: Uuid): List<SeriesLessonOptOut>
}

class ExposedSeriesLessonOptOutRepository(private val database: Database? = null) : SeriesLessonOptOutRepository {

    private suspend fun <T> query(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db = database) { block() } }

    override suspend fun save(optOut: SeriesLessonOptOut): SeriesLessonOptOut = query {
        SeriesLessonOptOutsTable.insert { row ->
            row[id] = optOut.id
            row[reservationId] = optOut.reservationId
            row[instanceId] = optOut.instanceId
            row[optedOutAt] = optOut.optedOutAt
            row[isLateCancellation] = optOut.isLateCancellation
        }
        optOut
    }

    override suspend fun findByReservationAndInstance(reservationId: Uuid, instanceId: Uuid): SeriesLessonOptOut? =
        query {
            SeriesLessonOptOutsTable.selectAll()
                .where {
                    (SeriesLessonOptOutsTable.reservationId eq reservationId) and
                            (SeriesLessonOptOutsTable.instanceId eq instanceId)
                }
                .map { it.toSeriesLessonOptOut() }
                .singleOrNull()
        }

    override suspend fun findByReservation(reservationId: Uuid): List<SeriesLessonOptOut> = query {
        SeriesLessonOptOutsTable.selectAll()
            .where { SeriesLessonOptOutsTable.reservationId eq reservationId }
            .map { it.toSeriesLessonOptOut() }
    }
}

fun ResultRow.toSeriesLessonOptOut(): SeriesLessonOptOut = SeriesLessonOptOut(
    id = this[SeriesLessonOptOutsTable.id],
    reservationId = this[SeriesLessonOptOutsTable.reservationId],
    instanceId = this[SeriesLessonOptOutsTable.instanceId],
    optedOutAt = this[SeriesLessonOptOutsTable.optedOutAt],
    isLateCancellation = this[SeriesLessonOptOutsTable.isLateCancellation],
)
