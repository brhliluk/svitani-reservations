package cz.svitaninymburk.projects.reservations.ui.events

import cz.svitaninymburk.projects.reservations.util.hourMinute
import cz.svitaninymburk.projects.reservations.ui.util.totalPriceLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.time.Clock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import js.objects.unsafeJso
import web.scroll.ScrollBehavior
import web.scroll.smooth
import web.scroll.start
import web.scroll.ScrollIntoViewOptions
import web.scroll.ScrollLogicalPosition
import web.window.window
import cz.svitaninymburk.projects.reservations.ui.components.CopyLinkButton
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.ui.events.usecase.LessonAction
import cz.svitaninymburk.projects.reservations.ui.events.usecase.isPastLesson
import cz.svitaninymburk.projects.reservations.ui.events.usecase.lessonAction
import cz.svitaninymburk.projects.reservations.util.humanReadable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun IComponent.SeriesCard(
    series: EventSeries,
    onSignUpClick: () -> Unit,
    /** Rezervace jedné lekce (drop-in); druhý argument = přihláška jako náhradník. */
    onLessonClick: ((EventInstance, Boolean) -> Unit)? = null,
) {
    val currentStrings by strings
    val scope = rememberCoroutineScope()
    val lessonsModel = remember(series.id) { buildCourseLessonsModel(scope, series.id) }

    div(className = "indicator w-full") {

        span(className = "hidden sm:inline-flex indicator-item badge badge-secondary font-bold shadow-md") {
            +currentStrings.course
        }

        // Klik kamkoli na kartu rozbalí termíny lekcí; tlačítka uvnitř si klik nechávají pro sebe.
        div(className = "card card-bordered bg-base-100 shadow-sm w-full transition hover:shadow-md hover:border-primary/50 cursor-pointer") {
            onClick { lessonsModel.toggle() }
            div(className = "card-body p-4 sm:p-6") {

                div(className = "sm:hidden") {
                    span(className = "badge badge-secondary font-bold") { +currentStrings.course }
                }

                div(className = "flex items-start justify-between gap-2") {
                    h3(className = "card-title text-lg sm:text-xl font-bold text-base-content") {
                        +series.title
                    }
                    span(className = "shrink-0") {
                        onClick { it.stopPropagation() }
                        CopyLinkButton(
                            url = "${window.location.origin}/?series=${series.id}",
                            label = currentStrings.copySeriesLink,
                        )
                    }
                }
                if (series.isCancelled) {
                    div(className = "badge badge-error badge-sm gap-1 mt-1") {
                        span(className = "icon-[heroicons--x-circle] size-3")
                        +currentStrings.cancelled
                    }
                }

                div(className = "flex gap-2 my-1") {
                    div(className = "badge badge-secondary badge-outline text-xs gap-1") {
                        span(className = "icon-[heroicons--academic-cap] size-3")
                        +"${series.lessonCount} ${currentStrings.courseLessons}"
                    }
                    if (series.showAttendeeCount) {
                        div(className = "badge badge-ghost text-xs gap-1") {
                            span(className = "icon-[heroicons--users] size-3")
                            +"Max ${series.capacity}"
                        }
                    }
                }

                div(className = "flex items-center gap-2 text-sm text-base-content/70 mt-2 bg-base-200/50 p-2 rounded-lg") {
                    span(className = "icon-[heroicons--calendar-days] size-5 text-primary")
                    span(className = "font-medium") {
                        +"${series.startDate} — ${series.endDate}"
                    }
                }

                val dow = series.lessonDayOfWeek
                val startT = series.lessonStartTime
                val endT = series.lessonEndTime
                if (dow != null && startT != null && endT != null) {
                    div(className = "flex items-center gap-2 text-sm text-base-content/70 mt-1 bg-base-200/50 p-2 rounded-lg") {
                        span(className = "icon-[heroicons--clock] size-5 text-primary/70")
                        span {
                            +currentStrings.lessonScheduleText(
                                dayIndex = dow.ordinal,
                                startTime = startT.hourMinute,
                                endTime = endT.hourMinute,
                            )
                        }
                    }
                }

                p(className = "py-3 text-base-content/80 text-sm leading-relaxed") {
                    +series.description
                }

                CourseLessons(lessonsModel, onLessonClick)

                div(className = "card-actions items-center justify-between mt-4 pt-4 border-t border-base-200") {
                    div(className = "flex flex-col") {
                        span(className = "text-xs text-base-content/60 font-medium uppercase tracking-wider") { +currentStrings.priceLabel }
                        span(className = "text-xl font-bold text-primary") {
                            +totalPriceLabel(series.price, currentStrings)
                        }
                    }

                    div(className = "flex flex-col items-end gap-1") {
                        if (series.isCancelled) {
                            button(className = "btn btn-error btn-outline rounded-full px-6 min-h-11", disabled = true) {
                                +currentStrings.cancelled
                            }
                        } else {
                            // Uzávěrku spočítal server — prohlížeč nemá databázi časových pásem.
                            val isDeadlinePassed = series.isReservationClosed(Clock.System.now())
                            button(className = "btn btn-primary rounded-full px-6 min-h-11 shadow-sm${if (isDeadlinePassed) " btn-disabled" else ""}") {
                                span(className = "icon-[heroicons--pencil-square] size-5")
                                +currentStrings.courseSignUp
                                onClick {
                                    it.stopPropagation()
                                    if (!isDeadlinePassed) onSignUpClick()
                                }
                            }
                            if (isDeadlinePassed) {
                                span(className = "text-xs text-base-content/60 text-right") {
                                    +(series.reservationDeadlineMessage ?: currentStrings.reservationClosed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
/** Přepínač a seznam termínů kurzu; klik dovnitř seznamu kartu nezabalí. */
@Composable
private fun IComponent.CourseLessons(model: CourseLessonsModel, onLessonClick: ((EventInstance, Boolean) -> Unit)?) {
    val currentStrings by strings

    button(className = "btn btn-ghost btn-sm min-h-11 self-start gap-1 px-2 text-primary") {
        attribute("aria-expanded", model.isExpanded.toString())
        onClick {
            it.stopPropagation()
            model.toggle()
        }
        span(className = "${if (model.isExpanded) "icon-[heroicons--chevron-up]" else "icon-[heroicons--chevron-down]"} size-4")
        +if (model.isExpanded) currentStrings.hideCourseLessons else currentStrings.showCourseLessons
    }

    if (!model.isExpanded) return

    div(className = "flex flex-col gap-2 cursor-default scroll-mt-4") {
        onClick { it.stopPropagation() }
        // Na telefonu bývá seznam pod dlouhým popisem mimo obrazovku — po rozbalení na něj sjedeme.
        val list = element
        LaunchedEffect(Unit) {
            if (window.innerWidth < SM_BREAKPOINT_PX) list.scrollIntoView(unsafeJso<ScrollIntoViewOptions> { behavior = ScrollBehavior.smooth; block = ScrollLogicalPosition.start })
        }
        val lessons = model.lessons
        val error = model.errorMessage
        when {
            model.isLoading -> span(className = "loading loading-spinner loading-sm text-primary self-center")
            error != null -> div(className = "alert alert-error text-sm py-2") {
                span(className = "icon-[heroicons--exclamation-circle] size-5")
                span(className = "flex-1") { +error }
                button(className = "btn btn-sm min-h-11") {
                    onClick { model.load() }
                    +currentStrings.retry
                }
            }
            lessons.isNullOrEmpty() -> p(className = "text-sm text-base-content/50 italic py-2") { +currentStrings.noLessonsYet }
            else -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                lessons.forEach { lesson ->
                    val isPast = isPastLesson(lesson, now)
                    div(className = "flex flex-wrap items-center gap-2 p-2 rounded-lg bg-base-200/50 text-sm text-base-content/70${if (isPast) " opacity-50" else ""}") {
                        span(className = "icon-[heroicons--clock] size-4 shrink-0")
                        span { +"${lesson.startDateTime.humanReadable}–${lesson.endDateTime.time.hourMinute}" }
                        when {
                            lesson.isCancelled -> div(className = "badge badge-error badge-sm") { +currentStrings.lessonCancelledBadge }
                            isPast -> div(className = "badge badge-ghost badge-sm") { +currentStrings.badgePast }
                        }
                        if (onLessonClick != null) LessonActionButton(lesson, isPast, onLessonClick)
                    }
                }
            }
        }
    }
}

/** Odpovídá Tailwind `sm` — pod ním je rozložení pro telefon. */
private const val SM_BREAKPOINT_PX = 640

@Composable
private fun IComponent.LessonActionButton(lesson: EventInstance, isPast: Boolean, onLessonClick: (EventInstance, Boolean) -> Unit) {
    val currentStrings by strings
    when (lessonAction(lesson, isPast, Clock.System.now())) {
        LessonAction.NONE -> {}
        LessonAction.RESERVE -> button(className = "btn btn-primary btn-sm min-h-11 rounded-full ml-auto") {
            onClick { onLessonClick(lesson, false) }
            +currentStrings.reserve
        }
        LessonAction.WAITLIST -> button(className = "btn btn-outline btn-secondary btn-sm min-h-11 rounded-full ml-auto") {
            onClick { onLessonClick(lesson, true) }
            +currentStrings.registerAsSubstitute
        }
        LessonAction.FULL -> div(className = "badge badge-error badge-sm ml-auto") { +currentStrings.capacityFull }
        LessonAction.CLOSED -> span(className = "text-xs text-base-content/60 ml-auto") {
            +(lesson.reservationDeadlineMessage ?: currentStrings.reservationClosed)
        }
    }
}
