package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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

    init {
        // Kontrola duplicity a insert běží v oddělených transakcích, takže dvojklik
        // dokáže uložit dva řádky. Dvě omluvenky na tutéž lekci pak odečtou místo
        // dvakrát a shodí každé další čtení té rezervace. Constraint je jediné
        // spolehlivé místo, kde to zarazit.
        uniqueIndex("ux_series_lesson_opt_outs_reservation_instance", reservationId, instanceId)
        // findByInstance se volá v cyklu přes všechny dnešní lekce (Admin.getDashboardSummary).
        index("ix_series_lesson_opt_outs_instance", false, instanceId)
    }
}

interface SeriesLessonOptOutRepository {
    suspend fun save(optOut: SeriesLessonOptOut): SeriesLessonOptOut

    /**
     * Uloží omluvenku, nebo vrátí null, pokud na tu dvojici (rezervace, lekce) už
     * jedna je. Kontrola předem nestačí — běží v jiné transakci než insert, takže
     * dvojklik jí proklouzne; poslední slovo má unique index.
     */
    suspend fun saveIfAbsent(optOut: SeriesLessonOptOut): SeriesLessonOptOut?
    suspend fun findByReservationAndInstance(reservationId: Uuid, instanceId: Uuid): SeriesLessonOptOut?
    suspend fun findByReservation(reservationId: Uuid): List<SeriesLessonOptOut>
    suspend fun findByInstance(instanceId: Uuid): List<SeriesLessonOptOut>

    /**
     * Smaže omluvenku a vrátí, jestli nějaká byla. Používá to jediné místo —
     * admin vrací účastníka do lekce, ze které se omluvil omylem.
     */
    suspend fun delete(reservationId: Uuid, instanceId: Uuid): Boolean
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

    override suspend fun saveIfAbsent(optOut: SeriesLessonOptOut): SeriesLessonOptOut? =
        try {
            save(optOut)
        } catch (e: ExposedSQLException) {
            if (e.isUniqueConstraintViolation()) null else throw e
        }

    override suspend fun findByReservationAndInstance(reservationId: Uuid, instanceId: Uuid): SeriesLessonOptOut? =
        query {
            SeriesLessonOptOutsTable.selectAll()
                .where {
                    (SeriesLessonOptOutsTable.reservationId eq reservationId) and
                            (SeriesLessonOptOutsTable.instanceId eq instanceId)
                }
                .map { it.toSeriesLessonOptOut() }
                // firstOrNull, ne singleOrNull — kdyby se do dat historicky dostala
                // duplicita, nesmí kvůli tomu spadnout každé další čtení rezervace.
                .firstOrNull()
        }

    override suspend fun findByReservation(reservationId: Uuid): List<SeriesLessonOptOut> = query {
        SeriesLessonOptOutsTable.selectAll()
            .where { SeriesLessonOptOutsTable.reservationId eq reservationId }
            .map { it.toSeriesLessonOptOut() }
    }

    override suspend fun findByInstance(instanceId: Uuid): List<SeriesLessonOptOut> = query {
        SeriesLessonOptOutsTable.selectAll()
            .where { SeriesLessonOptOutsTable.instanceId eq instanceId }
            .map { it.toSeriesLessonOptOut() }
    }

    override suspend fun delete(reservationId: Uuid, instanceId: Uuid): Boolean = query {
        SeriesLessonOptOutsTable.deleteWhere {
            (SeriesLessonOptOutsTable.reservationId eq reservationId) and
                    (SeriesLessonOptOutsTable.instanceId eq instanceId)
        } > 0
    }
}

fun ResultRow.toSeriesLessonOptOut(): SeriesLessonOptOut = SeriesLessonOptOut(
    id = this[SeriesLessonOptOutsTable.id],
    reservationId = this[SeriesLessonOptOutsTable.reservationId],
    instanceId = this[SeriesLessonOptOutsTable.instanceId],
    optedOutAt = this[SeriesLessonOptOutsTable.optedOutAt],
    isLateCancellation = this[SeriesLessonOptOutsTable.isLateCancellation],
)

/**
 * SQLite hlásí porušení unique indexu jako SQLITE_CONSTRAINT (kód 19); přesná
 * podoba zprávy se mezi verzemi ovladače liší, proto se testuje volně.
 */
private fun ExposedSQLException.isUniqueConstraintViolation(): Boolean {
    val text = message.orEmpty().lowercase()
    return "unique constraint" in text || "constraint failed" in text
}
