package cz.svitaninymburk.projects.reservations.ui.admin.events.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.events.LessonScheduleTable
import cz.svitaninymburk.projects.reservations.ui.admin.events.PriceCurrencyField
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.number.numeric
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.html.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import web.html.HTMLSelectElement

/** Rozpis kurzu: začátek, počet lekcí, den a čas — a živý náhled vygenerovaných lekcí. */
@Composable
fun IComponent.CourseScheduleCard(model: AdminCreateEventModel) {
    val currentStrings by strings
    div(className = "card bg-base-100 shadow-sm border-t-4 border-secondary") {
        div(className = "card-body") {
            div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                div(className = "form-control w-full") {
                    label(className = "label") { span(className = "label-text font-bold") { +currentStrings.startDateLabel } }
                    text(value = model.courseStartDate, type = InputType.Date, className = "input input-bordered w-full") {
                        onInput { model.onCourseStartDateChange(value ?: "") }
                    }
                }

                div(className = "form-control w-full") {
                    label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonCountLabel } }
                    div(className = "relative flex items-center") {
                        numeric(value = model.lessonCount, min = 1, decimals = 0, className = "input input-bordered w-full pr-16") {
                            attribute("step", "1")
                            onInput { model.onLessonCountChange(value?.toInt() ?: 1) }
                            onChange { model.onLessonCountChange(value?.toInt() ?: 1) }
                        }
                        span(className = "absolute right-4 text-base-content/50 text-sm") { +currentStrings.courseLessons }
                    }
                }

                div(className = "form-control w-full") {
                    label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonDayLabel } }
                    select(className = "select select-bordered w-full") {
                        option(value = "", label = currentStrings.lessonDayPlaceholder) {
                            if (model.courseLessonDayOrdinal == null) attribute("selected", "true")
                        }
                        DayOfWeek.entries.forEach { day ->
                            option(value = day.isoDayNumber.toString(), label = currentStrings.dayName(day.isoDayNumber - 1)) {
                                if (model.courseLessonDayOrdinal == day.isoDayNumber) attribute("selected", "true")
                            }
                        }
                        onChange { event ->
                            model.onCourseLessonDayChange((event.target as? HTMLSelectElement)?.value?.toIntOrNull())
                        }
                    }
                }

                div(className = "form-control w-full") {
                    label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonTimeLabel } }
                    text(value = model.courseLessonStartTimeStr, type = InputType.Time, className = "input input-bordered w-full") {
                        onInput { model.courseLessonStartTimeStr = value ?: "" }
                    }
                }

                PriceCurrencyField(
                    label = currentStrings.lessonPriceLabel,
                    value = model.courseLessonPrice,
                    hint = currentStrings.lessonPriceHint,
                ) { model.courseLessonPrice = it }
            }

            LessonScheduleTable(
                dates = model.computedCourseDates,
                dateOverrides = model.lessonDateOverrides,
                dropIn = model.lessonDropIn,
                isExcluded = model::isLessonExcluded,
                lessonStartTime = model.courseLessonStartTime,
                durationMinutes = model.durationHours * 60 + model.durationMinutes,
                onDateOverride = model::setLessonDateOverride,
                onToggleExcluded = model::toggleLessonExcluded,
                onDropInChange = model::setLessonDropIn,
                onAllDropInChange = model::setAllDropIn,
                showDateEditHint = true,
            )
        }
    }
}
