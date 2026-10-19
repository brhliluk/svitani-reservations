package cz.svitaninymburk.projects.reservations.ui.reservation

import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.NumberValue
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.isCustomFieldValid
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/*
 * Povinná vlastní pole se kontrolují na obsah podle typu, ne na přítomnost
 * klíče v mapě. Na přítomnost se spolehnout nelze: `renderCustomField` zapisuje
 * hodnotu při každém stisku klávesy a nikdy ji neodebere, takže vymazané pole
 * v mapě zůstává. Dřív tu byla kontrola `value != null && value.toString()
 * .isNotBlank()`, jenže CustomFieldValue jsou data classy a jejich toString()
 * vrací "TextValue(fieldKey=pozn, value=)" — blank tedy nebyl nikdy a vyplnit
 * povinné pole a zase ho vymazat prošlo.
 *
 * Stejnou funkci používá i zpětná vazba u pole (červený rámeček a hláška), aby
 * nemohla svítit chyba u pole, které odeslání nebrání, ani naopak.
 */

/** Akce 10:00–12:00, aby šlo hlídat, že časový rozsah leží uvnitř. */
private fun target(fields: List<CustomFieldDefinition>) = ReservationTarget.Instance(
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

private fun valid(field: CustomFieldDefinition, value: CustomFieldValue?): Boolean =
    isCustomFieldValid(field, value, target(listOf(field)))

private val TEXT = TextFieldDefinition(key = "pozn", label = "Poznámka", isRequired = true)
private val NUMBER = NumberFieldDefinition(key = "deti", label = "Počet dětí", isRequired = true)
private val NUMBER_RANGE = NumberFieldDefinition(key = "vek", label = "Věk", isRequired = true, min = 1, max = 18)
private val CHECK = BooleanFieldDefinition(key = "souhlas", label = "Souhlasím s podmínkami", isRequired = true)
private val RANGE = TimeRangeFieldDefinition(key = "cas", label = "Čas", isRequired = true)

private fun range(from: LocalTime, to: LocalTime) = TimeRangeValue("cas", from, to)

class RequiredTextFieldSpec {

    @Test
    fun filledTextPasses() {
        assertTrue(valid(TEXT, TextValue("pozn", "bez lepku")))
    }

    @Test
    fun untouchedFieldBlocks() {
        assertFalse(valid(TEXT, null))
    }

    @Test
    fun fieldFilledAndThenClearedBlocks() {
        // Klíč v mapě zůstane, ale obsah je prázdný — právě tohle dřív prošlo.
        assertFalse(valid(TEXT, TextValue("pozn", "")))
    }

    @Test
    fun spacesOnlyBlock() {
        assertFalse(valid(TEXT, TextValue("pozn", "   ")))
        assertFalse(valid(TEXT, TextValue("pozn", "\t\n")))
    }

    @Test
    fun wrongValueTypeCountsAsEmpty() {
        assertFalse(valid(TEXT, NumberValue("pozn", 5f)))
    }
}

class RequiredNumberFieldSpec {

    @Test
    fun filledNumberPasses() {
        assertTrue(valid(NUMBER, NumberValue("deti", 2f)))
    }

    @Test
    fun untouchedFieldBlocks() {
        assertFalse(valid(NUMBER, null))
        assertFalse(valid(NUMBER, TextValue("deti", "2")))
    }

    @Test
    fun zeroIsALegitimateAnswerWhenNoBoundsAreSet() {
        // Bez zadaných hranic je nula platné číslo. Vymazané pole se v
        // renderCustomField ukládá jako 0f, takže tady se nedá rozeznat od
        // zadané nuly — na to je potřeba zásah u vstupu, ne u validace.
        assertTrue(valid(NUMBER, NumberValue("deti", 0f)))
    }

    @Test
    fun valueOutsideBoundsBlocks() {
        // imaskNumeric u vstupu max neomezuje — do pole 1–18 se dá napsat 99,
        // takže hranice musí hlídat validace.
        assertFalse(valid(NUMBER_RANGE, NumberValue("vek", 0f)))
        assertFalse(valid(NUMBER_RANGE, NumberValue("vek", 99f)))
    }

    @Test
    fun boundsThemselvesAreInside() {
        assertTrue(valid(NUMBER_RANGE, NumberValue("vek", 1f)))
        assertTrue(valid(NUMBER_RANGE, NumberValue("vek", 18f)))
    }
}

class RequiredCheckboxSpec {

    @Test
    fun requiredCheckboxMustBeChecked() {
        // Povinný souhlas s podmínkami je přesně ten případ, pro který to platí.
        assertTrue(valid(CHECK, BooleanValue("souhlas", true)))
        assertFalse(valid(CHECK, BooleanValue("souhlas", false)))
        assertFalse(valid(CHECK, null))
    }

    @Test
    fun optionalCheckboxStaysOptional() {
        val optional = BooleanFieldDefinition(key = "newsletter", label = "Novinky", isRequired = false)
        assertTrue(valid(optional, BooleanValue("newsletter", false)))
        assertTrue(valid(optional, null))
    }
}

class RequiredTimeRangeSpec {

    @Test
    fun rangeInsideTheEventPasses() {
        assertTrue(valid(RANGE, range(LocalTime(10, 30), LocalTime(11, 30))))
    }

    @Test
    fun boundsThemselvesAreInside() {
        assertTrue(valid(RANGE, range(LocalTime(10, 0), LocalTime(12, 0))))
    }

    @Test
    fun emptyOrReversedRangeBlocks() {
        assertFalse(valid(RANGE, null))
        assertFalse(valid(RANGE, range(LocalTime(10, 0), LocalTime(10, 0))))
        assertFalse(valid(RANGE, range(LocalTime(11, 0), LocalTime(10, 30))))
    }

    @Test
    fun rangeReachingOutsideTheEventBlocks() {
        // Akce je 10:00–12:00.
        assertFalse(valid(RANGE, range(LocalTime(9, 0), LocalTime(11, 0))))
        assertFalse(valid(RANGE, range(LocalTime(11, 0), LocalTime(13, 0))))
    }
}

/** Nepovinné pole neblokuje odeslání nikdy, ať je v mapě cokoli. */
class OptionalCustomFieldSpec {

    @Test
    fun optionalFieldsPassInEveryState() {
        val cases = listOf<Pair<CustomFieldDefinition, CustomFieldValue?>>(
            TextFieldDefinition(key = "a", label = "A") to null,
            TextFieldDefinition(key = "a", label = "A") to TextValue("a", ""),
            NumberFieldDefinition(key = "b", label = "B", min = 5, max = 9) to NumberValue("b", 99f),
            TimeRangeFieldDefinition(key = "cas", label = "C") to range(LocalTime(9, 0), LocalTime(9, 0)),
        )
        cases.forEach { (field, value) -> assertTrue(valid(field, value), "pole '${field.key}'") }
    }
}
