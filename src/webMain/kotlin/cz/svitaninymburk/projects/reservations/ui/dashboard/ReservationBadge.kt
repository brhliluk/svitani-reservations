package cz.svitaninymburk.projects.reservations.ui.dashboard

import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation

/** Odznak stavu rezervace na dashboardu. */
enum class ReservationBadge { PAID, FREE, ON_SITE, WAITING }

/**
 * U akce zdarma nemá co svítit „Čeká na platbu" ani „Zaplaceno" — nic se
 * neplatilo. Platí to i pro historické rezervace, které v DB zůstaly ve stavu
 * PENDING_PAYMENT, proto se rozhoduje podle ceny, ne podle stavu.
 *
 * Pořadník je výjimka: dokud se přihláška nepovýší, místo ještě není, takže by
 * „Zdarma" slibovalo víc, než jak to je.
 */
fun reservationBadge(item: MyReservationListItem): ReservationBadge = when {
    item.isFree && item.status != Reservation.Status.WAITLISTED -> ReservationBadge.FREE
    item.status == Reservation.Status.CONFIRMED -> ReservationBadge.PAID
    item.paymentType == PaymentInfo.Type.ON_SITE -> ReservationBadge.ON_SITE
    else -> ReservationBadge.WAITING
}
