package cz.svitaninymburk.projects.reservations.event

fun calculateTotalPrice(
    basePrice: Double,
    seatCount: Int,
    customFields: List<CustomFieldDefinition>,
    customValues: Map<String, CustomFieldValue>
): Double {
    val fieldMap = customFields.associateBy { it.key }
    var total = basePrice * seatCount

    // Pass 1: multiplicative modifiers (TimeMultiplier)
    for ((key, value) in customValues) {
        val modifier = fieldMap[key]?.priceModifier ?: continue
        if (modifier is PriceModifier.TimeMultiplier) {
            val tv = value as? TimeRangeValue ?: continue
            val hours = hoursFromRange(tv)
            if (hours > 0) total *= hours
        }
    }

    // Pass 2: additive modifiers (FixedAmount, PerUnit)
    for ((key, value) in customValues) {
        val modifier = fieldMap[key]?.priceModifier ?: continue
        when (modifier) {
            is PriceModifier.FixedAmount -> {
                if ((value as? BooleanValue)?.value == true) total += modifier.amount
            }
            is PriceModifier.PerUnit -> {
                val n = (value as? NumberValue)?.value ?: continue
                total += modifier.pricePerUnit * n
            }
            is PriceModifier.TimeMultiplier -> Unit
        }
    }

    return total
}

fun hoursFromRange(value: TimeRangeValue): Double =
    (value.to.toSecondOfDay() - value.from.toSecondOfDay()) / 3600.0
