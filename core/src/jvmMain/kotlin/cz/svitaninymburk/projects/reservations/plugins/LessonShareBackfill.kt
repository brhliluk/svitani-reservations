package cz.svitaninymburk.projects.reservations.plugins

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/**
 * Doplní `lesson_share` (poměrnou část ceny za lessonCount, viz `Reservation.lessonShare`)
 * k zápisům na kurz, které vznikly dřív, než se při rezervaci začala ukládat.
 *
 * Počet lekcí v době rezervace se už zjistit nedá, proto se dělí všemi lekcemi
 * kurzu včetně zrušených — zrušená lekce byla v době zápisu nejspíš součástí kurzu
 * a do poměru patří. Dělení na celé koruny dolů (`CAST AS INTEGER` u kladných čísel),
 * stejně jako `lessonShareOf` pro nové rezervace.
 *
 * Vrací počet doplněných řádků. Idempotentní: sahá jen na řádky s NULL; kurz bez
 * lekcí nechá být a zkusí to při dalším startu znovu.
 */
internal fun JdbcTransaction.backfillLessonShares(): Int {
    val lessonCount = "(SELECT COUNT(*) FROM event_instances i WHERE i.series_id = reservations.reference_id)"
    val condition = "reference_type = 'SERIES' AND lesson_share IS NULL AND $lessonCount > 0"

    val affected = exec("SELECT count(*) FROM reservations WHERE $condition") { rs ->
        rs.next(); rs.getInt(1)
    } ?: 0
    if (affected == 0) return 0

    exec("UPDATE reservations SET lesson_share = CAST(MAX(total_price, 0) / $lessonCount AS INTEGER) WHERE $condition")
    return affected
}
