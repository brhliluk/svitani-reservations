package cz.svitaninymburk.projects.reservations.repository.attendance

import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.util.dbQuery
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

object ReservationAttendanceTable : Table("reservation_attendance") {
    val reservationId = reference(
        name = "reservation_id",
        refColumn = ReservationsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val checkedIn = bool("checked_in").default(false)
    val checkedInAt = timestamp("checked_in_at").nullable()

    override val primaryKey = PrimaryKey(reservationId)
}

class ExposedAttendanceRepository : AttendanceRepository {

    override suspend fun isCheckedIn(reservationId: Uuid): Boolean = dbQuery {
        ReservationAttendanceTable.selectAll()
            .where { ReservationAttendanceTable.reservationId eq reservationId }
            .singleOrNull()
            ?.get(ReservationAttendanceTable.checkedIn) ?: false
    }

    override suspend fun setCheckedIn(reservationId: Uuid, checkedIn: Boolean) = dbQuery {
        val now = if (checkedIn) Clock.System.now() else null
        val updatedRows = ReservationAttendanceTable.update(
            { ReservationAttendanceTable.reservationId eq reservationId }
        ) { row ->
            row[ReservationAttendanceTable.checkedIn] = checkedIn
            row[ReservationAttendanceTable.checkedInAt] = now
        }
        if (updatedRows == 0) {
            ReservationAttendanceTable.insert { row ->
                row[ReservationAttendanceTable.reservationId] = reservationId
                row[ReservationAttendanceTable.checkedIn] = checkedIn
                row[ReservationAttendanceTable.checkedInAt] = now
            }
        }
    }

    override suspend fun checkedInFlags(reservationIds: List<Uuid>): Map<Uuid, Boolean> = dbQuery {
        if (reservationIds.isEmpty()) return@dbQuery emptyMap()

        val found = ReservationAttendanceTable.selectAll()
            .where { ReservationAttendanceTable.reservationId inList reservationIds }
            .associate { row ->
                row[ReservationAttendanceTable.reservationId] to row[ReservationAttendanceTable.checkedIn]
            }

        reservationIds.associateWith { id -> found[id] ?: false }
    }
}
