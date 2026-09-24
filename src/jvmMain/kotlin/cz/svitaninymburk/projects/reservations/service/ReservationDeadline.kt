package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.util.APP_TIMEZONE
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Uzávěrka rezervace jako absolutní čas: [EventInstance.reservationDeadline] před
 * začátkem, počítáno v provozní zóně. Jediné místo, kde se počítá — server podle
 * ní rezervaci odmítne a stejnou hodnotu posílá klientovi v
 * [EventInstance.reservationClosesAt], který si ji sám spočítat neumí.
 */
internal fun EventInstance.computeReservationClosesAt(): Instant? =
    reservationDeadline?.let { startDateTime.toInstant(APP_TIMEZONE) - it }

/** U kurzu se uzávěrka měří od prvního dne (a času lekce, když ho kurz má). */
internal fun EventSeries.computeReservationClosesAt(): Instant? =
    reservationDeadline?.let { LocalDateTime(startDate, lessonStartTime ?: LocalTime(0, 0)).toInstant(APP_TIMEZONE) - it }

internal fun EventInstance.isReservationDeadlinePassed(now: Instant = Clock.System.now()): Boolean =
    computeReservationClosesAt()?.let { now >= it } ?: false

internal fun EventSeries.isReservationDeadlinePassed(now: Instant = Clock.System.now()): Boolean =
    computeReservationClosesAt()?.let { now >= it } ?: false

/** Kopie pro klienta s dopočítanou uzávěrkou. */
internal fun EventInstance.withReservationClosesAt(): EventInstance = copy(reservationClosesAt = computeReservationClosesAt())

internal fun EventSeries.withReservationClosesAt(): EventSeries = copy(reservationClosesAt = computeReservationClosesAt())
