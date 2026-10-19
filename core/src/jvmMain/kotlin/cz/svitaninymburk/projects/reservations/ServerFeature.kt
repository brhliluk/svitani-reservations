package cz.svitaninymburk.projects.reservations

import org.jetbrains.exposed.v1.core.Table
import org.koin.core.module.Module

/**
 * Volitelný modul aplikace na straně serveru (např. hlídání dětí). Jádro o modulech
 * neví; aplikace mu jejich seznam předá do [reservationsServer] a jádro je jen zapojí.
 */
class ServerFeature(
    /** Jméno do logu při startu. */
    val name: String,
    /** Tabulky modulu. Zakládají a migrují se spolu s tabulkami jádra. */
    val tables: List<Table> = emptyList(),
    val koinModules: List<Module> = emptyList(),
    /** Ukázková data modulu pro `LOAD_MOCK_DATA`; běží až po datech jádra. */
    val loadMockData: (suspend () -> Unit)? = null,
)
