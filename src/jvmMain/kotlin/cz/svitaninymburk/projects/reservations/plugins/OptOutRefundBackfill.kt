package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutsTable
import cz.svitaninymburk.projects.reservations.repository.wallet.WalletTransactionsTable
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Duration.Companion.minutes

/**
 * Omluvenky si dřív nepamatovaly, kolik za ně odešlo do peněženky — bylo to jen
 * v součtu transakcí LESSON_OPT_OUT_REFUND za rezervaci. Pod stejným důvodem ale
 * chodí i kredit za lekci zrušenou adminem (`cancelSeriesLesson`), takže ze součtu
 * nejde poznat, co patří které omluvence. Tenhle přepis to u historických řádků
 * dohledá podle času.
 *
 * Kredit za omluvenku se připisuje ve stejném požadavku, hned po uložení omluvenky
 * (`cancelReservation`), takže jeho transakce vzniká pár milisekund po `opted_out_at`.
 * Okno [MATCH_WINDOW] je s velkou rezervou; kredit za zrušenou lekci se do něj
 * trefit nemůže, protože zrušenou lekci už nikdo neomlouvá. Omluvenka bez
 * transakce v okně nic nedostala (nezaplaceno, pozdě, kurz nic nevrací) → 0.
 *
 * Idempotentní: sahá jen na řádky s NULL, které po prvním běhu nezůstanou.
 */
internal fun JdbcTransaction.backfillOptOutRefundedAmounts(): Int {
    val pending = SeriesLessonOptOutsTable.selectAll()
        .where { SeriesLessonOptOutsTable.refundedAmount.isNull() }
        .toList()
    if (pending.isEmpty()) return 0

    val reservationIds = pending.map { it[SeriesLessonOptOutsTable.reservationId] }.distinct()
    val credits = WalletTransactionsTable.selectAll()
        .where {
            (WalletTransactionsTable.reservationId inList reservationIds) and
                (WalletTransactionsTable.reason eq WalletTransactionReason.LESSON_OPT_OUT_REFUND) and
                (WalletTransactionsTable.amount greater 0.0)
        }
        .groupBy { it[WalletTransactionsTable.reservationId] }
        .mapValues { (_, rows) -> rows.sortedBy { it[WalletTransactionsTable.createdAt] }.toMutableList() }

    pending
        .sortedBy { it[SeriesLessonOptOutsTable.optedOutAt] }
        .forEach { row ->
            val optedOutAt = row[SeriesLessonOptOutsTable.optedOutAt]
            val unused = credits[row[SeriesLessonOptOutsTable.reservationId]]
            val match = if (row[SeriesLessonOptOutsTable.isLateCancellation]) null
            else unused?.firstOrNull {
                val at = it[WalletTransactionsTable.createdAt]
                at >= optedOutAt && at <= optedOutAt + MATCH_WINDOW
            }
            // Jedna transakce patří jedné omluvence — dvě omluvenky těsně po sobě
            // si ji nesmí přivlastnit obě.
            if (match != null) unused?.remove(match)
            val amount = match?.get(WalletTransactionsTable.amount) ?: 0.0

            SeriesLessonOptOutsTable.update({ SeriesLessonOptOutsTable.id eq row[SeriesLessonOptOutsTable.id] }) {
                it[refundedAmount] = amount
            }
        }
    return pending.size
}

private val MATCH_WINDOW = 2.minutes
