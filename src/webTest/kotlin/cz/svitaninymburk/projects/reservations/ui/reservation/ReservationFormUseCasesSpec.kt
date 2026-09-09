package cz.svitaninymburk.projects.reservations.ui.reservation

import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.PriceModifier
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.WALLET_CODE_LENGTH
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.areCustomFieldsValid
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.effectivePaymentType
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.formatPriceHours
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.isCompleteWalletCode
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.isContactValid
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.showsPaymentTypePicker
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.submittedSeatCount
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.timeMultiplierHours
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.walletDeduction
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Akce 10:00–12:00, aby šlo hlídat, že časový rozsah leží uvnitř. */
private fun target(fields: List<cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition> = emptyList()) =
    ReservationTarget.Instance(
        EventInstance(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            title = "Akce",
            description = "",
            startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
            endDateTime = LocalDateTime(2099, 6, 1, 12, 0),
            price = 100.0,
            capacity = 10,
            occupiedSpots = 0,
            customFields = fields,
        )
    )

private fun range(from: LocalTime, to: LocalTime, key: String = "cas") =
    TimeRangeValue(fieldKey = key, from = from, to = to)

class ReservationFormWalletSpec {

    @Test
    fun onlyAFullCodeIsWorthVerifying() {
        assertFalse(isCompleteWalletCode(""))
        assertFalse(isCompleteWalletCode("ABC123"))
        assertTrue(isCompleteWalletCode("A".repeat(WALLET_CODE_LENGTH)))
        // Delší kód taky neprojde — je to přesná délka, ne minimum.
        assertFalse(isCompleteWalletCode("A".repeat(WALLET_CODE_LENGTH + 1)))
    }

    @Test
    fun walletPaysAtMostTheWholePrice() {
        // Zbytek kreditu se nepřevádí ani nevrací.
        assertEquals(300.0, walletDeduction(balance = 500.0, total = 300.0))
        assertEquals(200.0, walletDeduction(balance = 200.0, total = 300.0))
    }

    @Test
    fun noWalletMeansNoDeduction() {
        assertEquals(0.0, walletDeduction(balance = null, total = 300.0))
        assertEquals(0.0, walletDeduction(balance = 0.0, total = 300.0))
    }

    @Test
    fun paymentPickerHidesOnceThereIsNothingLeftToPay() {
        assertTrue(showsPaymentTypePicker(total = 300.0, deduction = 100.0))
        assertFalse(showsPaymentTypePicker(total = 300.0, deduction = 300.0))
    }

    @Test
    fun fullyCoveredReservationGoesThroughAsFree() {
        assertEquals(
            PaymentInfo.Type.FREE,
            effectivePaymentType(total = 300.0, deduction = 300.0, chosen = PaymentInfo.Type.BANK_TRANSFER),
        )
        // Akce zdarma taky: nula pokrytá nulou je celá cena.
        assertEquals(
            PaymentInfo.Type.FREE,
            effectivePaymentType(total = 0.0, deduction = 0.0, chosen = PaymentInfo.Type.ON_SITE),
        )
    }

    @Test
    fun partiallyCoveredReservationKeepsTheChosenPaymentType() {
        assertEquals(
            PaymentInfo.Type.ON_SITE,
            effectivePaymentType(total = 300.0, deduction = 100.0, chosen = PaymentInfo.Type.ON_SITE),
        )
    }
}

class ReservationFormValidationSpec {

    @Test
    fun contactNeedsAllFourFields() {
        assertTrue(isContactValid("Jan", "Novák", "jan@example.com", "+420123456789"))
        assertFalse(isContactValid("", "Novák", "jan@example.com", "+420123456789"))
        assertFalse(isContactValid("Jan", "  ", "jan@example.com", "+420123456789"))
        assertFalse(isContactValid("Jan", "Novák", "jan-bez-zavinace", "+420123456789"))
        assertFalse(isContactValid("Jan", "Novák", "jan@example.com", "nesmysl"))
    }

    @Test
    fun optionalFieldsNeverBlockSubmitting() {
        val t = target(
            listOf(
                TextFieldDefinition(key = "pozn", label = "Poznámka", isRequired = false),
                TimeRangeFieldDefinition(key = "cas", label = "Čas", isRequired = false),
            )
        )
        assertTrue(areCustomFieldsValid(t, emptyMap()))
    }

    @Test
    fun requiredCheckboxPassesEvenUnchecked() {
        // "Povinné" u zaškrtávátka znamená, že se má zobrazit, ne že musí být ano.
        val t = target(listOf(BooleanFieldDefinition(key = "souhlas", label = "Souhlas", isRequired = true)))
        assertTrue(areCustomFieldsValid(t, emptyMap()))
    }

    @Test
    fun untouchedRequiredTextBlocksSubmitting() {
        val t = target(listOf(TextFieldDefinition(key = "pozn", label = "Poznámka", isRequired = true)))
        assertFalse(areCustomFieldsValid(t, emptyMap()))
        assertTrue(areCustomFieldsValid(t, mapOf("pozn" to TextValue("pozn", "něco") as CustomFieldValue)))
    }

    /**
     * POZOR: tenhle test popisuje chování, které formulář má, ne chování, které
     * je správné. Kontrola je `value.toString().isNotBlank()`, ale TextValue je
     * data class, takže toString() vrací "TextValue(fieldKey=pozn, value=)" a
     * blank není nikdy. renderCustomField navíc zapisuje hodnotu do mapy při
     * každém stisku klávesy a nikdy ji neodebere, takže vyplnit povinné pole a
     * zase ho vymazat projde. Až se to opraví, test má spadnout — a má se
     * přepsat, ne smazat.
     */
    @Test
    fun requiredTextPassesOnceTouchedEvenIfCleared() {
        val t = target(listOf(TextFieldDefinition(key = "pozn", label = "Poznámka", isRequired = true)))
        assertTrue(areCustomFieldsValid(t, mapOf("pozn" to TextValue("pozn", "") as CustomFieldValue)))
        assertTrue(areCustomFieldsValid(t, mapOf("pozn" to TextValue("pozn", "   ") as CustomFieldValue)))
    }

    @Test
    fun requiredTimeRangeMustBeFilledAndNonEmpty() {
        val t = target(listOf(TimeRangeFieldDefinition(key = "cas", label = "Čas", isRequired = true)))
        assertFalse(areCustomFieldsValid(t, emptyMap()))
        // od == do je prázdný rozsah
        assertFalse(areCustomFieldsValid(t, mapOf("cas" to range(LocalTime(10, 0), LocalTime(10, 0)))))
        // obrácený rozsah
        assertFalse(areCustomFieldsValid(t, mapOf("cas" to range(LocalTime(11, 0), LocalTime(10, 30)))))
        assertTrue(areCustomFieldsValid(t, mapOf("cas" to range(LocalTime(10, 30), LocalTime(11, 30)))))
    }

    @Test
    fun requiredTimeRangeMustFitInsideTheEventWindow() {
        // Akce je 10:00–12:00.
        val t = target(listOf(TimeRangeFieldDefinition(key = "cas", label = "Čas", isRequired = true)))
        assertFalse(areCustomFieldsValid(t, mapOf("cas" to range(LocalTime(9, 0), LocalTime(11, 0)))))
        assertFalse(areCustomFieldsValid(t, mapOf("cas" to range(LocalTime(11, 0), LocalTime(13, 0)))))
        // Krajní hodnoty se počítají jako uvnitř.
        assertTrue(areCustomFieldsValid(t, mapOf("cas" to range(LocalTime(10, 0), LocalTime(12, 0)))))
    }

    @Test
    fun oneInvalidFieldBlocksTheWholeForm() {
        val t = target(
            listOf(
                TextFieldDefinition(key = "ok", label = "OK", isRequired = true),
                NumberFieldDefinition(key = "chybi", label = "Chybí", isRequired = true),
            )
        )
        assertFalse(areCustomFieldsValid(t, mapOf("ok" to TextValue("ok", "ano") as CustomFieldValue)))
    }
}

class ReservationFormPriceBreakdownSpec {

    @Test
    fun waitlistAlwaysHoldsASingleSeat() {
        assertEquals(1, submittedSeatCount(asWaitlist = true, seats = 4))
        assertEquals(4, submittedSeatCount(asWaitlist = false, seats = 4))
    }

    @Test
    fun hoursDropTheTrailingZero() {
        assertEquals("2", formatPriceHours(2.0))
        assertEquals("1.5", formatPriceHours(1.5))
        // Zaokrouhluje se na jedno desetinné místo.
        assertEquals("1.3", formatPriceHours(1.333))
        assertEquals("2", formatPriceHours(1.98))
    }

    @Test
    fun onlyATimeFieldThatMultipliesPriceShowsUpInTheBreakdown() {
        val multiplying = TimeRangeFieldDefinition(
            key = "cas",
            label = "Čas",
            priceModifier = PriceModifier.TimeMultiplier,
        )
        val t = target(listOf(multiplying))
        assertEquals(1.5, timeMultiplierHours(t, mapOf("cas" to range(LocalTime(10, 0), LocalTime(11, 30)))))
    }

    @Test
    fun timeFieldWithoutAPriceModifierIsIgnored() {
        val plain = TimeRangeFieldDefinition(key = "cas", label = "Čas")
        val t = target(listOf(plain))
        assertNull(timeMultiplierHours(t, mapOf("cas" to range(LocalTime(10, 0), LocalTime(11, 30)))))
    }

    @Test
    fun unfilledOrEmptyRangeMeansNothingToMultiply() {
        val multiplying = TimeRangeFieldDefinition(
            key = "cas",
            label = "Čas",
            priceModifier = PriceModifier.TimeMultiplier,
        )
        val t = target(listOf(multiplying))
        assertNull(timeMultiplierHours(t, emptyMap()))
        assertNull(timeMultiplierHours(t, mapOf("cas" to range(LocalTime(10, 0), LocalTime(10, 0)))))
    }
}
