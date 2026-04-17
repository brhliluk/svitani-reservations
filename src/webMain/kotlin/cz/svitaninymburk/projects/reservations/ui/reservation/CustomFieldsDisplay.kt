package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.NumberValue
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.span

@Composable
fun IComponent.CustomFieldsDisplay(
    fields: List<CustomFieldDefinition>,
    values: Map<String, CustomFieldValue>,
) {
    if (fields.isEmpty()) return
    val currentStrings by strings

    div(className = "flex flex-col gap-3") {
        fields.forEach { field ->
            div(className = "flex flex-col sm:flex-row sm:justify-between sm:items-baseline gap-1 sm:gap-4") {
                span(className = "text-xs uppercase font-bold tracking-wider text-base-content/60") {
                    +field.label
                }
                span(className = "font-medium text-base-content text-sm sm:text-right break-words") {
                    +formatCustomFieldValue(field, values[field.key], currentStrings.yes, currentStrings.no)
                }
            }
        }
    }
}

private fun formatCustomFieldValue(
    field: CustomFieldDefinition,
    value: CustomFieldValue?,
    yesLabel: String,
    noLabel: String,
): String = when (field) {
    is TextFieldDefinition -> (value as? TextValue)?.value?.takeIf { it.isNotBlank() } ?: "—"
    is NumberFieldDefinition -> (value as? NumberValue)?.value?.let {
        if (it % 1f == 0f) it.toInt().toString() else it.toString()
    } ?: "—"
    is BooleanFieldDefinition -> if ((value as? BooleanValue)?.value == true) yesLabel else noLabel
    is TimeRangeFieldDefinition -> (value as? TimeRangeValue)?.let { "${it.from} – ${it.to}" } ?: "—"
}
