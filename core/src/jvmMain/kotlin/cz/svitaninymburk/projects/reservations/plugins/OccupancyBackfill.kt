package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.repository.event.INACTIVE_RESERVATION_STATUSES
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
 *
 * Vrací, kolik hodnot musel opravit. Zápisové cesty mají čítače držet v souladu
 * s rezervacemi, takže v produkci by tu měly být samé nuly — z logu se to dá
 * ověřit, než se čítače zruší úplně
 * (`docs/followups/varianta-c-zruseni-ulozene-obsazenosti.md`).
 */
internal fun JdbcTransaction.recomputeOccupiedSpotsInTransaction(): OccupancyDrift {
    // Stavy se skládají z INACTIVE_RESERVATION_STATUSES, aby se SQL nemohlo rozejít s Kotlinem.
    val inactive = INACTIVE_RESERVATION_STATUSES.joinToString(",") { "'${it.name}'" }

    fun seats(table: String, referenceType: String) = """
        (SELECT COALESCE(SUM(r.seat_count), 0) FROM reservations r
          WHERE r.reference_type = '$referenceType'
            AND r.reference_id = $table.id
            AND r.status NOT IN ($inactive))
    """.trimIndent()

    // Pořadník se počítá po přihláškách, ne po místech: attemptToReserveWaitlistSpot
    // zvedá čítač o 1 a strop se kontroluje jako occupiedWaitlist + 1 <= waitlistCapacity.
    // Proto COUNT(*), ne SUM(seat_count).
    fun waitlisted(table: String, referenceType: String) = """
        (SELECT COUNT(*) FROM reservations r
          WHERE r.reference_type = '$referenceType'
            AND r.reference_id = $table.id
            AND r.status = 'WAITLISTED')
    """.trimIndent()

    // Přepisuje se jen to, co nesedí, a počet se vrací — stejně jako confirmFreeReservations.
    fun recompute(table: String, column: String, derived: String): Int {
        val drifted = exec("SELECT count(*) FROM $table WHERE $column <> $derived") { rs ->
            rs.next(); rs.getInt(1)
        } ?: 0
        if (drifted > 0) exec("UPDATE $table SET $column = $derived WHERE $column <> $derived")
        return drifted
    }

    return OccupancyDrift(
        instanceSpots = recompute("event_instances", "occupied_spots", seats("event_instances", "INSTANCE")),
        seriesSpots = recompute("event_series", "occupied_spots", seats("event_series", "SERIES")),
        instanceWaitlist = recompute("event_instances", "occupied_waitlist", waitlisted("event_instances", "INSTANCE")),
        seriesWaitlist = recompute("event_series", "occupied_waitlist", waitlisted("event_series", "SERIES")),
    )
}

/** Kolik lekcí a kurzů mělo při startu uložený čítač jinak, než říkají rezervace. */
data class OccupancyDrift(
    val instanceSpots: Int,
    val seriesSpots: Int,
    val instanceWaitlist: Int,
    val seriesWaitlist: Int,
) {
    val total: Int get() = instanceSpots + seriesSpots + instanceWaitlist + seriesWaitlist

    override fun toString(): String =
        "místa: $instanceSpots lekcí, $seriesSpots kurzů; pořadník: $instanceWaitlist lekcí, $seriesWaitlist kurzů"
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
