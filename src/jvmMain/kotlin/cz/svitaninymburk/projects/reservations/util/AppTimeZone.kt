package cz.svitaninymburk.projects.reservations.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Provozní časová zóna. Časy akcí se ukládají jako `LocalDateTime` bez zóny
 * a myslí se jimi pražský čas — podle Prahy se proto musí počítat každá hranice
 * („už začalo“, „už proběhlo“, uzávěrka omluvenek 18:00 den předem).
 *
 * Produkce nemá připnuté TZ a `TimeZone.currentSystemDefault()` by na UTC hostu
 * posunul všechny hranice o jednu až dvě hodiny. Na serveru se proto systémová
 * zóna nepoužívá nikde, jen tahle konstanta.
 *
 * Jen pro jvmMain: prohlížeč databázi časových pásem nemá a `TimeZone.of` by
 * v něm spadl — klient dostává hranice spočítané serverem jako `Instant`.
 */
internal val APP_TIMEZONE: TimeZone = TimeZone.of("Europe/Prague")

/** Teď v provozní zóně — pro porovnání s časy akcí. */
internal fun nowInAppTimeZone(clock: Clock = Clock.System): LocalDateTime =
    clock.now().toLocalDateTime(APP_TIMEZONE)
