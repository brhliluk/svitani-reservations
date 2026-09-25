package cz.svitaninymburk.projects.reservations.plugins

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/**
 * Rezervace na akce zdarma vznikaly dřív stejně jako placené — ve stavu
 * PENDING_PAYMENT. Uživateli tím v detailu svítil QR kód na 0 Kč, admin je viděl
 * mezi nezaplacenými (`Admin.kt`) a `hasPendingReservations()`
 * (`repository/reservation/Database.kt`) kvůli nim natrvalo držel naživo
 * dotazování FIO API, přestože je nikdo nikdy nezaplatí. Server je od teď zakládá
 * rovnou potvrzené (viz `createReservationFlow` v `service/Reservation.kt`), tenhle
 * přepis dorovná historické řádky.
 *
 * Vrací počet přepsaných řádků, ať je v logu vidět, co se stalo. Idempotentní:
 * druhý běh už nic nenajde. Záměrně sahá jen na PENDING_PAYMENT — pořadník
 * (WAITLISTED) se na potvrzené překlápí až povýšením a CANCELLED/REJECTED musí
 * zůstat, jak jsou. Peníze to nikde nehýbe: `paid_amount` u těchto rezervací je
 * nula, takže ani storno nespustí refundy (`RefundService.refundWholeReservation`
 * vrací null pro nulovou zaplacenou částku).
 *
 * Obsazenost se tím nemění — čítače počítají všechny stavy kromě
 * CANCELLED/REJECTED/WAITLISTED (viz `recomputeOccupiedSpotsInTransaction`
 * a `INACTIVE_RESERVATION_STATUSES` v `repository/event/SeriesLessonLoad.kt`),
 * takže přechod PENDING_PAYMENT → CONFIRMED je pro kapacitu neutrální.
 */
internal fun JdbcTransaction.confirmFreeReservations(): Int {
    val condition = "total_price <= 0 AND status = 'PENDING_PAYMENT'"

    val affected = exec("SELECT count(*) FROM reservations WHERE $condition") { rs ->
        rs.next(); rs.getInt(1)
    } ?: 0
    if (affected == 0) return 0

    exec("UPDATE reservations SET status = 'CONFIRMED', payment_type = 'FREE' WHERE $condition")
    return affected
}
