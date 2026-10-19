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

    @Test
    fun twoTimeMultipliersCompoundMultiplicatively() {
        // Two time-range fields both with TimeMultiplier: total multiplied by both hour values
        // (100.0 × 1 seat) × 2.0h × 1.5h = 300.0
        val fields = listOf(
            TimeRangeFieldDefinition("time1", "Morning", priceModifier = PriceModifier.TimeMultiplier),
            TimeRangeFieldDefinition("time2", "Afternoon", priceModifier = PriceModifier.TimeMultiplier)
        )
        val values = mapOf(
            "time1" to TimeRangeValue("time1", LocalTime(9, 0), LocalTime(11, 0)),  // 2 hours
            "time2" to TimeRangeValue("time2", LocalTime(14, 0), LocalTime(15, 30)) // 1.5 hours
        )
        assertEquals(300.0, calculateTotalPrice(100.0, 1, fields, values))
    }

    @Test
    fun tieredAmountMatchesExactTier() {
        // 100.0 × 1 seat + tier(count=2, price=200) = 300.0
        val fields = listOf(
            NumberFieldDefinition("children", "Children", priceModifier = PriceModifier.TieredAmount(
                tiers = listOf(
                    PriceModifier.TieredAmount.Tier(1, 150.0),
                    PriceModifier.TieredAmount.Tier(2, 200.0)
                ),
                fallbackPerUnit = 0.0
            ))
        )
        val values = mapOf("children" to NumberValue("children", 2f))
        assertEquals(300.0, calculateTotalPrice(100.0, 1, fields, values))
    }

    @Test
    fun tieredAmountBeyondLastTierUsesFallback() {
        // 100.0 × 1 + tier(count=2, price=200) + (4-2) × 50.0 = 400.0
        val fields = listOf(
            NumberFieldDefinition("children", "Children", priceModifier = PriceModifier.TieredAmount(
                tiers = listOf(
                    PriceModifier.TieredAmount.Tier(1, 150.0),
                    PriceModifier.TieredAmount.Tier(2, 200.0)
                ),
                fallbackPerUnit = 50.0
            ))
        )
        val values = mapOf("children" to NumberValue("children", 4f))
        assertEquals(400.0, calculateTotalPrice(100.0, 1, fields, values))
    }

    @Test
    fun tieredAmountBeyondLastTierWithZeroFallback() {
        // 100.0 × 1 + tier(count=3, price=250) + (5-3) × 0 = 350.0
        val fields = listOf(
            NumberFieldDefinition("children", "Children", priceModifier = PriceModifier.TieredAmount(
                tiers = listOf(
                    PriceModifier.TieredAmount.Tier(1, 150.0),
                    PriceModifier.TieredAmount.Tier(3, 250.0)
                ),
                fallbackPerUnit = 0.0
            ))
        )
        val values = mapOf("children" to NumberValue("children", 5f))
        assertEquals(350.0, calculateTotalPrice(100.0, 1, fields, values))
    }
}
