package cz.svitaninymburk.projects.reservations.ui.admin.events

import cz.svitaninymburk.projects.reservations.util.hourMinute
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.computeLessonEndTime
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.text.text
import dev.kilua.html.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Náhled lekcí kurzu — jeden řádek na vygenerovaný termín, včetně vyřazených.
 * Sdílí ho zakládání akce typu kurz (`create/`) i zakládání série nad šablonou
 * (`series/`); obě obrazovky drží stejný stav (overrides / drop-in / vyřazené)
 * klíčovaný **původním** indexem, aby vyřazení lekce neposunulo nastavení ostatních.
 */
@Composable
fun IComponent.LessonScheduleTable(
    dates: List<LocalDate>,
    dateOverrides: Map<Int, String>,
    dropIn: Map<Int, Boolean>,
    isExcluded: (Int) -> Boolean,
    lessonStartTime: LocalTime?,
    durationMinutes: Int?,
    onDateOverride: (index: Int, value: String, defaultDate: String) -> Unit,
    onToggleExcluded: (index: Int) -> Unit,
    onDropInChange: (index: Int, value: Boolean) -> Unit,
    onAllDropInChange: (all: Boolean) -> Unit,
    className: String? = null,
    showDateEditHint: Boolean = false,
) {
    val currentStrings by strings
    if (dates.isEmpty()) return

    val endTimeStr = if (lessonStartTime != null && durationMinutes != null) {
        computeLessonEndTime(lessonStartTime, durationMinutes).hourMinute
    } else "?"
    val startTimeStr = lessonStartTime?.hourMinute ?: "?"

    val keptIndices = dates.indices.filterNot(isExcluded)
    val excludedCount = dates.size - keptIndices.size

    div(className = listOfNotNull("mt-4", className).joinToString(" ")) {
        div(className = "flex items-center gap-2 mb-2") {
            span(className = "icon-[heroicons--calendar-days] size-5 text-secondary")
            span(className = "font-medium text-sm") { +currentStrings.lessonPreviewHeading(keptIndices.size) }
            if (showDateEditHint) {
                span(className = "text-xs text-base-content/50") { +currentStrings.lessonDateEditHint }
            }
            if (excludedCount > 0) {
                span(className = "text-xs text-base-content/50") { +currentStrings.lessonExcludedSummary(excludedCount) }
            }
        }
        div(className = "overflow-x-auto") {
            table(className = "table table-xs w-full") {
                val allDropIn = keptIndices.isNotEmpty() && keptIndices.all { dropIn[it] == true }
                thead {
                    tr {
                        th(className = "w-8") { +"#" }
                        th { +currentStrings.tableHeaderDate }
                        th { +currentStrings.tableHeaderTime }
                        th {
                            div(className = "flex items-center gap-2") {
                                label(className = "cursor-pointer tooltip tooltip-left") {
                                    attribute("data-tip", currentStrings.lessonIndividualBulkTooltip)
                                    checkBox(value = allDropIn, className = "checkbox checkbox-secondary checkbox-xs") {
                                        onChange { onAllDropInChange(value) }
                                    }
                                }
                                span(className = "tooltip tooltip-left cursor-help whitespace-nowrap") {
                                    attribute("data-tip", currentStrings.lessonIndividualTooltip)
                                    +currentStrings.lessonIndividualLabel
                                    span(className = "icon-[heroicons--question-mark-circle] size-3 text-base-content/40 ml-1")
                                }
                            }
                        }
                        th(className = "text-right") { +currentStrings.tableHeaderActions }
                    }
                }
                tbody {
                    var lessonNumber = 0
                    dates.forEachIndexed { i, defaultDate ->
                        val effectiveDateStr = dateOverrides[i] ?: defaultDate.toString()
                        val excluded = isExcluded(i)
                        if (!excluded) lessonNumber++
                        val displayNumber = if (excluded) "–" else lessonNumber.toString()
                        tr(className = if (excluded) "opacity-50" else null) {
                            td(className = "text-base-content/50") { +displayNumber }
                            td {
                                if (excluded) {
                                    div(className = "flex items-center gap-2") {
                                        span(className = "line-through text-base-content/60") { +effectiveDateStr }
                                        span(className = "badge badge-ghost badge-xs") { +currentStrings.lessonExcludedBadge }
                                    }
                                } else {
                                    text(value = effectiveDateStr, type = InputType.Date, className = "input input-xs input-bordered w-36") {
                                        onInput { onDateOverride(i, value ?: "", defaultDate.toString()) }
                                    }
                                }
                            }
                            td(className = "text-sm text-base-content/70") {
                                if (excluded) {
                                    span(className = "line-through") { +"$startTimeStr – $endTimeStr" }
                                } else {
                                    +"$startTimeStr – $endTimeStr"
                                }
                            }
                            td {
                                if (!excluded) {
                                    label(className = "cursor-pointer") {
                                        checkBox(value = dropIn[i] ?: false, className = "checkbox checkbox-secondary checkbox-xs") {
                                            onChange { onDropInChange(i, value) }
                                        }
                                    }
                                }
                            }
                            td(className = "text-right") {
                                button(className = "btn btn-ghost btn-xs tooltip tooltip-left ${if (excluded) "" else "text-error"}") {
                                    attribute(
                                        "data-tip",
                                        if (excluded) currentStrings.lessonRestoreTooltip else currentStrings.lessonExcludeTooltip,
                                    )
                                    span(
                                        className = if (excluded) {
                                            "icon-[heroicons--arrow-uturn-left] size-4"
                                        } else {
                                            "icon-[heroicons--x-mark] size-4"
                                        },
                                    )
                                    onClick { onToggleExcluded(i) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Čas ve tvaru `9:05`, jak ho tabulka lekcí ukazuje na obou obrazovkách. */
