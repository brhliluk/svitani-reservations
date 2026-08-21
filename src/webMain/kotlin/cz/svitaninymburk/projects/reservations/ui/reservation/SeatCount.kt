package cz.svitaninymburk.projects.reservations.ui.reservation

import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget

/**
 * Počet míst, se kterým formulář dál pracuje po zadání [typed].
 *
 * U akcí bez volby počtu míst ([ReservationTarget.allowMultipleSeats] = false) se pole vůbec
 * nezobrazuje, takže výsledek je vždy 1. Jinak se hodnota srazí do rozsahu 1..zbývající kapacita.
 */
fun ReservationTarget.clampSeatCount(typed: Int?): Int =
    if (!allowMultipleSeats) 1 else (typed ?: 1).coerceIn(1, maxCapacity)

/**
 * Zda zadaný počet míst přesahuje zbývající kapacitu — u akcí bez volby počtu míst nikdy,
 * protože uživatel žádnou hodnotu zadat nemůže.
 */
fun ReservationTarget.exceedsRemainingCapacity(typed: Int?): Boolean =
    allowMultipleSeats && (typed ?: 1) > maxCapacity
