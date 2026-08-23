package cz.svitaninymburk.projects.reservations.plugins

import kotlinx.coroutines.withContext
import io.ktor.server.application.Application
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Uložené čítače obsazenosti drží jen přímé rezervace na danou akci; zbytek se
 * dopočítává. Dokud omluvenky dekrementovaly čítač lekce, který přihláška na kurz
 * nikdy nenavýšila, sjížděly hodnoty do záporu — tenhle přepočet je vrátí na
 * pravdu odvozenou z rezervací. Idempotentní.
 */
suspend fun recomputeOccupiedSpots() = withContext(Dispatchers.IO) {
    // `transaction { }` (ne `dbQuery`) — jen ono nese JdbcTransaction receiver, bez
    // kterého se holé `exec(...)` nenajde. Stejně to dělá plugins/Database.kt.
    transaction {
        // Stavy jsou tu vypsané jako literál shodně s INACTIVE_RESERVATION_STATUSES
        // z repository/event/SeriesLessonLoad.kt — druhé a poslední místo, kde žije.
        exec("""
            UPDATE event_instances SET occupied_spots = (
                SELECT COALESCE(SUM(r.seat_count), 0) FROM reservations r
                 WHERE r.reference_type = 'INSTANCE'
                   AND r.reference_id = event_instances.id
                   AND r.status NOT IN ('CANCELLED','REJECTED','WAITLISTED')
            )
        """.trimIndent())

        exec("""
            UPDATE event_series SET occupied_spots = (
                SELECT COALESCE(SUM(r.seat_count), 0) FROM reservations r
                 WHERE r.reference_type = 'SERIES'
                   AND r.reference_id = event_series.id
                   AND r.status NOT IN ('CANCELLED','REJECTED','WAITLISTED')
            )
        """.trimIndent())
    }
}

fun Application.startOccupancyBackfill() {
    val logger = KtorSimpleLogger("OccupancyBackfill")
    launch(Dispatchers.IO) {
        try {
            recomputeOccupiedSpots()
            logger.info("Occupancy backfill completed")
        } catch (e: Exception) {
            logger.error("Occupancy backfill failed", e)
        }
    }
}
