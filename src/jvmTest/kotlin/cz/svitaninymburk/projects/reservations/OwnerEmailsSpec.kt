package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import kotlin.test.Test
import kotlin.test.assertEquals

class OwnerEmailsSpec {

    @Test
    fun singleValidEmailPassesThroughUnchanged() {
        assertEquals(listOf("a@x.cz"), parseOwnerEmails(listOf("a@x.cz")))
    }

    @Test
    fun commaSeparatedPairBothValid() {
        assertEquals(listOf("a@x.cz", "b@y.cz"), parseOwnerEmails(listOf("a@x.cz,b@y.cz")))
    }

    @Test
    fun commaSpaceSeparatedPairBothValid() {
        assertEquals(listOf("a@x.cz", "b@y.cz"), parseOwnerEmails(listOf("a@x.cz, b@y.cz")))
    }

    @Test
    fun spaceSeparatedPairBothValid() {
        assertEquals(listOf("a@x.cz", "b@y.cz"), parseOwnerEmails(listOf("a@x.cz b@y.cz")))
    }

    @Test
    fun invalidTokensAreDroppedButValidOnesKept() {
        assertEquals(listOf("a@x.cz"), parseOwnerEmails(listOf("a@x.cz, not-an-email, ")))
    }

    @Test
    fun allInvalidInputReturnsEmptyList() {
        assertEquals(emptyList(), parseOwnerEmails(listOf("not-an-email", "also invalid")))
    }

    @Test
    fun duplicateAddressesAcrossRowsAreDeduped() {
        assertEquals(listOf("a@x.cz"), parseOwnerEmails(listOf("a@x.cz", "a@x.cz")))
    }

    @Test
    fun blankOrWhitespaceOnlyRowContributesNothing() {
        assertEquals(emptyList(), parseOwnerEmails(listOf("", "   ")))
    }
}
