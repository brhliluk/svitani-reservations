package cz.svitaninymburk.projects.reservations.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchSpec {

    @Test
    fun blankInputIsNoSearch() {
        assertNull(searchQueryOf(""))
        assertNull(searchQueryOf("   "))
        assertNull(searchQueryOf("\t\n"))
    }

    @Test
    fun nonBlankInputPassesThroughUntouched() {
        assertEquals("Novák", searchQueryOf("Novák"))
        // Mezery kolem dotazu se schválně nezahazují — RPC i lokální filtr si
        // s nimi poradí a uživatel uvidí, co napsal.
        assertEquals(" Novák ", searchQueryOf(" Novák "))
    }
}
