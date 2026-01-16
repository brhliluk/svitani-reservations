package cz.svitaninymburk.projects.reservations.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant


@Serializable
sealed interface CustomFieldValue {
    val fieldKey: String
}

@Serializable
@SerialName("text_value")
data class TextValue(
    override val fieldKey: String,
    val value: String
) : CustomFieldValue

@Serializable
@SerialName("number_value")
data class NumberValue(
    override val fieldKey: String,
    val value: Int
) : CustomFieldValue

@Serializable
@SerialName("boolean_value")
data class BooleanValue(
    override val fieldKey: String,
    val value: Boolean
) : CustomFieldValue

@Serializable
@SerialName("time_range_value")
data class TimeRangeValue(
    override val fieldKey: String,
    val from: Instant,
    val to: Instant,
) : CustomFieldValue