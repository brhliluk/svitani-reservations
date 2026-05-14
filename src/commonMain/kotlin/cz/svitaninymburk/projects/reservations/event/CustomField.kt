package cz.svitaninymburk.projects.reservations.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
sealed interface PriceModifier {
    @Serializable @SerialName("time_multiplier")
    object TimeMultiplier : PriceModifier

    @Serializable @SerialName("fixed_amount")
    data class FixedAmount(val amount: Double) : PriceModifier

    @Serializable @SerialName("per_unit")
    data class PerUnit(val pricePerUnit: Double) : PriceModifier
}

@Serializable
sealed interface CustomFieldDefinition {
    val key: String
    val label: String
    val isRequired: Boolean
    val priceModifier: PriceModifier?
}

@Serializable
@SerialName("text")
data class TextFieldDefinition(
    override val key: String,
    override val label: String,
    override val isRequired: Boolean = false,
    val isMultiline: Boolean = false,
    override val priceModifier: PriceModifier? = null
) : CustomFieldDefinition

@Serializable
@SerialName("number")
data class NumberFieldDefinition(
    override val key: String,
    override val label: String,
    override val isRequired: Boolean = false,
    val min: Int? = null,
    val max: Int? = null,
    override val priceModifier: PriceModifier? = null
) : CustomFieldDefinition

@Serializable
@SerialName("boolean")
data class BooleanFieldDefinition(
    override val key: String,
    override val label: String,
    override val isRequired: Boolean = false,
    override val priceModifier: PriceModifier? = null
) : CustomFieldDefinition

@Serializable
@SerialName("time_range")
data class TimeRangeFieldDefinition(
    override val key: String,
    override val label: String,
    override val isRequired: Boolean = false,
    override val priceModifier: PriceModifier? = null
) : CustomFieldDefinition
