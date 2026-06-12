package cz.svitaninymburk.projects.reservations.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneNumberTest {

    // --- normalize ---

    @Test
    fun normalizeCzWithSpacesAndPlus() {
        assertEquals("+420123456789", PhoneNumber.normalize("+420 123 456 789"))
    }

    @Test
    fun normalizeBareNineDigitsDefaultsToCz() {
        assertEquals("+420123456789", PhoneNumber.normalize("123456789"))
    }

    @Test
    fun normalizeLeadingTrunkZeroDropped() {
        assertEquals("+420123456789", PhoneNumber.normalize("0123456789"))
    }

    @Test
    fun normalizeDoubleZeroInternationalPrefix() {
        assertEquals("+420123456789", PhoneNumber.normalize("00420123456789"))
    }

    @Test
    fun normalizeCountryCodeWithoutPlus() {
        assertEquals("+420123456789", PhoneNumber.normalize("420123456789"))
    }

    @Test
    fun normalizeSlovak() {
        assertEquals("+421905123456", PhoneNumber.normalize("+421 905 123 456"))
    }

    @Test
    fun normalizeStripsSeparators() {
        assertEquals("+420123456789", PhoneNumber.normalize("(420) 123-456-789"))
    }

    @Test
    fun normalizeEmptyIsNull() {
        assertNull(PhoneNumber.normalize(""))
        assertNull(PhoneNumber.normalize("   "))
    }

    @Test
    fun normalizeGarbageIsNull() {
        assertNull(PhoneNumber.normalize("abc"))
    }

    // --- format ---

    @Test
    fun formatCanonicalCz() {
        assertEquals("+420 123 456 789", PhoneNumber.format("+420123456789"))
    }

    @Test
    fun formatBareNumber() {
        assertEquals("+420 123 456 789", PhoneNumber.format("123 456 789"))
    }

    @Test
    fun formatSlovak() {
        assertEquals("+421 905 123 456", PhoneNumber.format("00421905123456"))
    }

    @Test
    fun formatUnnormalizableReturnedAsIs() {
        assertEquals("abc", PhoneNumber.format("abc"))
        assertEquals("", PhoneNumber.format(""))
    }

    // --- isValid ---

    @Test
    fun isValidCzNineDigits() {
        assertTrue(PhoneNumber.isValid("+420 123 456 789"))
        assertTrue(PhoneNumber.isValid("123456789"))
    }

    @Test
    fun isValidSlovakNineDigits() {
        assertTrue(PhoneNumber.isValid("+421905123456"))
    }

    @Test
    fun isInvalidCzWrongLength() {
        assertFalse(PhoneNumber.isValid("12345678"))     // 8 digits
        assertFalse(PhoneNumber.isValid("1234567890"))   // 10 digits
    }

    @Test
    fun isInvalidEmpty() {
        assertFalse(PhoneNumber.isValid(""))
    }

    @Test
    fun isInvalidForeignNumberNotAccepted() {
        // Zahraniční čísla mimo CZ/SK validace nepodporuje
        assertFalse(PhoneNumber.isValid("+16502530000"))
        assertFalse(PhoneNumber.isValid("+447911123456"))
    }
}
