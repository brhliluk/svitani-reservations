package cz.svitaninymburk.projects.reservations.ui.util

import cz.svitaninymburk.projects.reservations.i18n.cs.CsStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class PriceLabelSpec {
    @Test
    fun celeKorunyBezDesetinneTecky() {
        assertEquals("150", formatAmountNumber(150.0))
        assertEquals("0", formatAmountNumber(0.0))
    }

    @Test
    fun halereSeNeztraceji() {
        assertEquals("150.5", formatAmountNumber(150.5))
        assertEquals("33.33", formatAmountNumber(100.0 / 3))
        assertEquals("0.05", formatAmountNumber(0.05))
    }

    @Test
    fun zapornaCastkaDrziZnamenko() {
        assertEquals("-0.5", formatAmountNumber(-0.5))
        assertEquals("-50", formatAmountNumber(-50.0))
    }

    @Test
    fun pohybVPenezenceMaZnamenko() {
        assertEquals("+50 Kč", signedAmount(50.0, CsStrings))
        assertEquals("−50 Kč", signedAmount(-50.0, CsStrings))
    }

    @Test
    fun nulovaCenaJeZdarma() {
        assertEquals(CsStrings.free, totalPriceLabel(0.0, CsStrings))
        assertEquals("200 Kč", totalPriceLabel(200.0, CsStrings))
    }
}
