package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Vygeneruje variabilní symbol, který ještě žádná rezervace nemá.
 * Vrací null, když se to ani po 10 pokusech nepovede — volající se pak rozhodne,
 * jestli to je důvod k chybě (viz `createReservationFlow`), nebo ne (povýšení z pořadníku).
 */
internal suspend fun ReservationRepository.generateUniqueVariableSymbol(): String? {
    var attempts = 0
    var vs: String

    do {
        if (attempts > 10) return null
        vs = generateCandidateVS()
        val exists = existsByVariableSymbol(vs)
        attempts++

    } while (exists)

    return vs
}

private fun generateCandidateVS(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    // 1. ROK (2 znaky): "26"
    val year = now.year.toString().takeLast(2)
    // 2. DEN V ROCE (3 znaky): "030" (30. leden)
    val dayOfYear = now.dayOfYear.toString().padStart(3, '0')

    // 3. NÁHODA (5 znaků): "12345"
    // Celkem 2 + 3 + 5 = 10 znaků (Maximum pro banky)
    val random = (0..99999).random().toString().padStart(5, '0')

    return "$year$dayOfYear$random"
}
