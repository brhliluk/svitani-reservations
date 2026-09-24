package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlin.math.floor

/**
 * Kredit za jednu lekci kurzu pro celou rezervaci (všechna její místa), zatím bez
 * stropu na zaplacenou částku — ten si každé místo použití přidává samo, protože
 * odečítá různé věci (už vrácené omluvenky, zbytek z celé rezervace).
 *
 * Ruční sazba kurzu ([EventSeries.lessonRefundAmount]) platí za jedno místo, takže
 * se násobí. Bez ní se vrací poměrná část ceny ([Reservation.lessonShare]), ve které
 * už jsou všechna místa i příplatky z vlastních polí. Nula v sazbě znamená „nevracet“.
 *
 * Jediné místo, kde to pravidlo žije — omluvenka, dodatečná vratka po zaplacení,
 * vzetí omluvenky zpět i zrušení lekce adminem musí počítat stejně.
 */
internal fun lessonCreditFor(reservation: Reservation, series: EventSeries?): Double =
    series?.lessonRefundAmount?.let { it * reservation.seatCount }
        ?: reservation.lessonShare
        ?: 0.0

/**
 * Poměrná část ceny za jednu lekci, dolů na celé koruny — strop na zaplacenou
 * částku pak zaručí, že se nikdy nevrátí víc, než přišlo. null = kurz bez lekcí.
 */
internal fun lessonShareOf(totalPrice: Double, lessonCount: Int): Double? =
    if (lessonCount <= 0) null else floor(totalPrice / lessonCount).coerceAtLeast(0.0)
