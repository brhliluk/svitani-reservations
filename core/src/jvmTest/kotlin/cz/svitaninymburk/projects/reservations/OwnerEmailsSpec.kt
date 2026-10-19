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

    @Test
    fun trailingSemicolonIsStripped() {
        assertEquals(listOf("a@x.cz"), parseOwnerEmails(listOf("a@x.cz;")))
    }

    @Test
    fun semicolonSeparatedPairBothValid() {
        assertEquals(listOf("a@x.cz", "b@y.cz"), parseOwnerEmails(listOf("a@x.cz;b@y.cz")))
    }

    @Test
    fun semicolonSpaceSeparatedPairBothValid() {
        assertEquals(listOf("a@x.cz", "b@y.cz"), parseOwnerEmails(listOf("a@x.cz; b@y.cz")))
    }

    @Test
    fun semicolonOnlyRowContributesNothing() {
        assertEquals(emptyList(), parseOwnerEmails(listOf(";", " ; ")))
    }

    @Test
    fun addressWithCharactersIllegalForSmtpIsDropped() {
        assertEquals(emptyList(), parseOwnerEmails(listOf("a@x.cz>")))
        assertEquals(emptyList(), parseOwnerEmails(listOf("\"a@x.cz")))
        assertEquals(emptyList(), parseOwnerEmails(listOf("a@x.cz:25")))
    }
}
