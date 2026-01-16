package cz.svitaninymburk.projects.reservations.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
sealed interface CustomFieldDefinition {
    val key: String
    val label: String
    val isRequired: Boolean
}

@Serializable
@SerialName("text")
data class TextFieldDefinition(
    override val key: String,
    override val label: String,
    override val isRequired: Boolean = false,
    val isMultiline: Boolean = false
) : CustomFieldDefinition

@Serializable
@SerialName("number")
data class NumberFieldDefinition(
    override val key: String,
    override val label: String,
    override val isRequired: Boolean = false,
    val min: Int? = null,
    val max: Int? = null
) : CustomFieldDefinition

@Serializable
@SerialName("boolean")
data class BooleanFieldDefinition(
    override val key: String,
    override val label: String,
    override val isRequired: Boolean = false
) : CustomFieldDefinition

@Serializable
@SerialName("time_range")
data class TimeRangeFieldDefinition(
    override val key: String,
    override val label: String,
    override val isRequired: Boolean = false
) : CustomFieldDefinition
