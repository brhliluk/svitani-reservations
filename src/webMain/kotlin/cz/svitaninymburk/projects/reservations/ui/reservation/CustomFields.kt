package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.Composable
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
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.NumberFormControl
import dev.kilua.form.check.checkBox
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.Time
import dev.kilua.html.div
import dev.kilua.html.label
import dev.kilua.html.span
import js.decorators.DecoratorContextKind
import js.json.isRawJSON
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun IComponent.renderCustomField(
    field: CustomFieldDefinition,
    stateMap: MutableMap<String, CustomFieldValue>
) {
    when (field) {
        // --- TEXT ---
        is TextFieldDefinition -> {
            label(className = "form-control w-full") {
                div(className = "label") {
                    span(className = "label-text") { +field.label }
                }

                if (field.isMultiline) {
                    textArea(value = (stateMap[field.key] as? TextValue)?.value, className = "textarea textarea-bordered h-24") {
                        onInput { stateMap[field.key] = TextValue(field.key, this.value ?: "") }
                    }
                } else {
                    text(value = (stateMap[field.key] as? TextValue)?.value, className = "input input-bordered w-full") {
                        required(field.isRequired)
                        onInput { stateMap[field.key] = TextValue(field.key, this.value ?: "") }
                    }
                }
            }
        }

        // --- NUMBER ---
        is NumberFieldDefinition -> {
            label(className = "form-control w-full") {
                div(className = "label") {
                    span(className = "label-text") { +field.label }
                }
                text(value = (stateMap[field.key] as? NumberValue)?.value?.toString(), type = InputType.Number, className = "input input-bordered w-full") {
                    field.min?.let { attribute("min", it.toString()) }
                    field.max?.let { attribute("max", it.toString()) }
//                    bind(field.key, object : NumberFormControl {}) TODO: if setAttr not enough
                    required(field.isRequired)
                    onInput { stateMap[field.key] = NumberValue(field.key, this.value?.toFloatOrNull() ?: 0f) }
                }
            }
        }

        // --- BOOLEAN ---
        is BooleanFieldDefinition -> {
            label(className = "label cursor-pointer justify-start gap-4 mt-2") {
                checkBox(value = (stateMap[field.key] as? BooleanValue)?.value ?: false, className = "checkbox checkbox-primary") {
                    require(field.isRequired)
                    onChange { stateMap[field.key] = BooleanValue(field.key, this.value) }
                }
                span(className = "label-text font-medium") { +field.label }
            }
        }

        // --- TIME RANGE (Od - Do) ---
        is TimeRangeFieldDefinition -> {
            var currentPair = (stateMap[field.key] as? TimeRangeValue).run { this?.from to this?.to }

            div(className = "form-control w-full") {
                div(className = "label") {
                    span(className = "label-text") { +field.label }
                }
                div(className = "flex gap-2 items-center") {
                    // OD
                    text(value = currentPair.first?.toString(), type = InputType.Time, className = "input input-bordered w-1/2") {
                        required(field.isRequired)
                        onInput {
                            this.value?.let {
                                currentPair = currentPair.copy(first = it.toLocalTime())
                                stateMap[field.key] = TimeRangeValue(field.key, it.toLocalTime(), currentPair.second ?: it.toLocalTime())
                            }
                        }
                    }
                    span { +"-" }
                    // DO
                    text(value = currentPair.second?.toString(), type = InputType.Time, className = "input input-bordered w-1/2") {
                        required(field.isRequired)
                        onInput {
                            this.value?.let {
                                currentPair = currentPair.copy(second = it.toLocalTime())
                                stateMap[field.key] = TimeRangeValue(field.key, currentPair.first ?: it.toLocalTime(), it.toLocalTime())
                            }
                        }
                    }
                }
            }
        }
    }
}

fun String?.toLocalTime(): LocalTime {
    if (isNullOrBlank()) return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

    val parts = split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return LocalTime(hour, minute)
}
