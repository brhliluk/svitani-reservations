package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.isFreePrice
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.span

/** Stav rezervace, jak ho ukazují přehledy; vykresluje ho [StatusBadge]. */
enum class ReservationStatusBadge { CANCELLED, WAITLISTED, FREE, PAID, ON_SITE, WAITING }

/**
 * Jedno místo, které rozhoduje, jak se rezervace v přehledu tváří. Admin tabulky
 * se dřív rozhodovaly podle tří příznaků (zrušeno / zaplaceno / na místě) a
 * všechno ostatní padalo do "Čeká" — náhradník tak byl k nerozeznání od
 * nezaplacené rezervace a akce zdarma se tvářila jako zaplacená převodem.
 *
 * Pořadí větví je podstatné:
 *  - zrušená/odmítnutá vítězí vždy, o platbě už nemá smysl mluvit,
 *  - náhradník je před cenou: dokud se přihláška nepovýší, místo ještě není,
 *    takže "Zdarma" by slibovalo víc, než jak to je,
 *  - nulová cena je před stavem, protože i historická rezervace, která zůstala
 *    v PENDING_PAYMENT, je pořád zdarma (viz confirmFreeReservations).
 */
fun reservationStatusBadge(
    status: Reservation.Status,
    paymentType: PaymentType,
    totalPrice: Double,
): ReservationStatusBadge = when {
    status == Reservation.Status.CANCELLED || status == Reservation.Status.REJECTED -> ReservationStatusBadge.CANCELLED
    status == Reservation.Status.WAITLISTED -> ReservationStatusBadge.WAITLISTED
    isFreePrice(totalPrice) -> ReservationStatusBadge.FREE
    status == Reservation.Status.CONFIRMED -> ReservationStatusBadge.PAID
    paymentType == PaymentType.ON_SITE -> ReservationStatusBadge.ON_SITE
    else -> ReservationStatusBadge.WAITING
}

/**
 * Tlačítko "Označit jako zaplaceno" má smysl jen tam, kde `markReservationAsPaid`
 * projde — ta vyžaduje stav PENDING_PAYMENT (viz service/Admin.kt). U náhradníka
 * nebo zrušené rezervace by skončilo chybou, u akce zdarma není co platit.
 */
fun canBeMarkedAsPaid(badge: ReservationStatusBadge): Boolean =
    badge == ReservationStatusBadge.WAITING || badge == ReservationStatusBadge.ON_SITE

/** Badge stavu rezervace — stejný v Mých rezervacích, v detailu akce i v seznamu rezervací. */
@Composable
fun IComponent.StatusBadge(badge: ReservationStatusBadge) {
    val currentStrings by strings
    // Celé názvy tříd, ne složené — Tailwind je hledá ve zdrojácích doslova.
    val (className, icon, label) = when (badge) {
        ReservationStatusBadge.CANCELLED -> Triple("badge-error", "icon-[heroicons--x-mark]", currentStrings.cancelled)
        // Pořadník má vlastní jméno: "Čeká" se u něj čte jako "čeká na platbu",
        // i když se čeká na uvolněné místo.
        ReservationStatusBadge.WAITLISTED -> Triple("badge-secondary badge-outline", "icon-[heroicons--queue-list]", currentStrings.waitlistedStatus)
        ReservationStatusBadge.FREE -> Triple("badge-success badge-outline", "icon-[heroicons--gift]", currentStrings.free)
        ReservationStatusBadge.PAID -> Triple("badge-success", "icon-[heroicons--check]", currentStrings.paid)
        ReservationStatusBadge.ON_SITE -> Triple("badge-info badge-outline", "icon-[heroicons--banknotes]", currentStrings.statusOnSiteBadge)
        ReservationStatusBadge.WAITING -> Triple("badge-warning", "icon-[heroicons--clock]", currentStrings.statusWaiting)
    }
    div(className = "badge $className gap-1 whitespace-nowrap") {
        span(className = "$icon size-3")
        +label
    }
}
