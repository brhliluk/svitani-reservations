package cz.svitaninymburk.projects.reservations.service

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Seznam akcí v adminu si popisek ceny skládá na serveru, kde není i18n po ruce.
 * Nula tam patří jako „Zdarma", ne „0.0 Kč".
 */
class AdminPriceLabelSpec {

    @Test
    fun `zero price is labelled as free`() {
        assertEquals("Zdarma", adminPriceLabel(0.0))
    }

    @Test
    fun `non-zero price keeps the amount with currency`() {
        assertEquals("150.0 Kč", adminPriceLabel(150.0))
    }
}
