package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.event.*
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PriceCalculatorSpec {

    @Test
    fun baselineNoPriceModifiers() {
        val fields = listOf(
            TextFieldDefinition("note", "Note")
        )
        val values = mapOf("note" to TextValue("note", "hello"))
        assertEquals(200.0, calculateTotalPrice(100.0, 2, fields, values))
    }

    @Test
    fun timeMultiplierExactFraction() {
        // 100.0 base × 2 seats × 1.5 hours = 300.0
        val fields = listOf(
            TimeRangeFieldDefinition("time", "Time", priceModifier = PriceModifier.TimeMultiplier)
        )
        val values = mapOf("time" to TimeRangeValue("time", LocalTime(9, 0), LocalTime(10, 30)))
        assertEquals(300.0, calculateTotalPrice(100.0, 2, fields, values))
    }

    @Test
    fun timeMultiplierIgnoredWhenNoValueSubmitted() {
        val fields = listOf(
            TimeRangeFieldDefinition("time", "Time", priceModifier = PriceModifier.TimeMultiplier)
        )
        val values = emptyMap<String, CustomFieldValue>()
        assertEquals(200.0, calculateTotalPrice(100.0, 2, fields, values))
    }

    @Test
    fun booleanFlatFeeAddedWhenTrue() {
        // 100.0 × 1 seat + 50.0 flat fee = 150.0
        val fields = listOf(
            BooleanFieldDefinition("extra", "Extra", priceModifier = PriceModifier.FixedAmount(50.0))
        )
        val values = mapOf("extra" to BooleanValue("extra", true))
        assertEquals(150.0, calculateTotalPrice(100.0, 1, fields, values))
    }

    @Test
    fun booleanFlatFeeIgnoredWhenFalse() {
        val fields = listOf(
            BooleanFieldDefinition("extra", "Extra", priceModifier = PriceModifier.FixedAmount(50.0))
        )
        val values = mapOf("extra" to BooleanValue("extra", false))
        assertEquals(100.0, calculateTotalPrice(100.0, 1, fields, values))
    }

    @Test
    fun numberPerUnitAdded() {
        // 100.0 × 1 seat + (3 × 30.0) = 190.0
        val fields = listOf(
            NumberFieldDefinition("extra", "Extra", priceModifier = PriceModifier.PerUnit(30.0))
        )
        val values = mapOf("extra" to NumberValue("extra", 3f))
        assertEquals(190.0, calculateTotalPrice(100.0, 1, fields, values))
    }

    @Test
    fun multiplicativePassRunsBeforeAdditivePass() {
        // TimeMultiplier then FixedAmount:
        // (100.0 × 2 × 1.5) + 50.0 = 350.0, NOT (100.0 × 2 + 50.0) × 1.5
        val fields = listOf(
            TimeRangeFieldDefinition("time", "Time", priceModifier = PriceModifier.TimeMultiplier),
            BooleanFieldDefinition("extra", "Extra", priceModifier = PriceModifier.FixedAmount(50.0))
        )
        val values = mapOf(
            "time" to TimeRangeValue("time", LocalTime(9, 0), LocalTime(10, 30)),
            "extra" to BooleanValue("extra", true)
        )
        assertEquals(350.0, calculateTotalPrice(100.0, 2, fields, values))
    }
}
