package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlinx.datetime.LocalDateTime

/**
 * Kurz běží, jakmile začala jeho první nezrušená lekce. Od té chvíle se kurz
 * neruší celý — ani rezervace na něj (storno celé rezervace), ani kurz jako
 * takový: ruší se jen lekce, které ještě nezačaly. Stejná hranice pro obojí,
 * ať admin nenarazí na kurz, který jde zrušit, ale jeho rezervace ne.
 */
internal fun List<EventInstance>.courseHasStarted(now: LocalDateTime): Boolean =
    any { !it.isCancelled && it.startDateTime <= now }

/** Lekce, které se ještě dají zrušit — nezrušené a nezačaté. */
internal fun List<EventInstance>.lessonsNotYetStarted(now: LocalDateTime): List<EventInstance> =
    filter { !it.isCancelled && it.startDateTime > now }
