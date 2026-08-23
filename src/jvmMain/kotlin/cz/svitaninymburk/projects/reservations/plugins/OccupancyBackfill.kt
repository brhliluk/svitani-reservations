package cz.svitaninymburk.projects.reservations.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Uložené čítače obsazenosti drží jen přímé rezervace na danou akci; zbytek se
 * dopočítává. Dokud omluvenky dekrementovaly čítač lekce, který přihláška na kurz
 * nikdy nenavýšila, sjížděly hodnoty do záporu — tenhle přepočet je vrátí na
 * pravdu odvozenou z rezervací. Idempotentní.
 *
 * Musí doběhnout synchronně na konci `configureDatabases()`, dřív než cokoli
 * jiného uvidí databázi: `MockDataLoader` sází `occupiedSpots` bez odpovídajících
 * rezervací (viz `mock/MockDataLoader.kt`) a asynchronní přepočet, který by s ním
 * závodil, by tahle testovací data vynuloval. V produkci `createReservation`
 * commitne `attemptToReserveSpots` (+N) a uložení rezervace ve dvou různých
 * transakcích — přepočet spuštěný mezi nimi by čerstvě založenou rezervaci ještě
 * neviděl a tiše by o ni srazil čítač (přeprodané místo).
 */
internal fun JdbcTransaction.recomputeOccupiedSpotsInTransaction() {
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

/**
 * Suspendující obal nad [recomputeOccupiedSpotsInTransaction] — držíme ho jen pro
 * volání mimo blokující kontext (`OccupancyBackfillTest`). Ostrý start jede přes
 * `recomputeOccupiedSpotsInTransaction()` přímo z `configureDatabases()`, aby
 * doběhl synchronně a nezávodil s mock loaderem ani s první rezervací.
 */
suspend fun recomputeOccupiedSpots() = withContext(Dispatchers.IO) {
    // `transaction { }` (ne `dbQuery`) — jen ono nese JdbcTransaction receiver, bez
    // kterého se holé `exec(...)` nenajde. Stejně to dělá plugins/Database.kt.
    transaction { recomputeOccupiedSpotsInTransaction() }
}
