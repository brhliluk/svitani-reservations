package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlinx.datetime.LocalDateTime

/**
 * Kurz běží, jakmile začala jeho první nezrušená lekce. Od té chvíle kurz jako
 * takový ruší jen lekce, které ještě nezačaly, a zákazník už svou rezervaci celou
 * nezruší. Admin ji zrušit může, ale s ručně zadanou vratkou
 * ([RefundService.startedCourseCancellation]).
 */
internal fun List<EventInstance>.courseHasStarted(now: LocalDateTime): Boolean =
    any { !it.isCancelled && it.startDateTime <= now }

/** Lekce, které se ještě dají zrušit — nezrušené a nezačaté. */
internal fun List<EventInstance>.lessonsNotYetStarted(now: LocalDateTime): List<EventInstance> =
    filter { !it.isCancelled && it.startDateTime > now }

/** Lekce, které už začaly (a nebyly zrušené) — u nich se kurz „odchodil“. */
internal fun List<EventInstance>.lessonsAlreadyStarted(now: LocalDateTime): List<EventInstance> =
    filter { !it.isCancelled && it.startDateTime <= now }
