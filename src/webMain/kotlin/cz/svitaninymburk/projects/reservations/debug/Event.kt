package cz.svitaninymburk.projects.reservations.debug

import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlinx.datetime.LocalDateTime

val randomEventList: List<EventInstance> get() = listOf(
    EventInstance(
        "0", "1",
        "Smyslohrátky", "Druhé smyslohrátky letošní",
        LocalDateTime(2026, 12, 12, 12, 12),
        LocalDateTime(2026, 12, 12, 14, 12),
        100.0,
        30
    ),
    EventInstance(
        "1", "0",
        "Herna", "Letsgooo",
        LocalDateTime(2026, 12, 24, 12, 12),
        LocalDateTime(2026, 12, 24, 14, 12),
        0.0,
        30
    ),
)