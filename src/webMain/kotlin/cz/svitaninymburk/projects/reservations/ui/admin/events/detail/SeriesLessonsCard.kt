package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.html.*
import kotlin.uuid.Uuid

@Composable
fun IComponent.SeriesLessonsCard(
    lessons: List<EventInstance>?,
    togglingDropInId: Uuid?,
    onAddLesson: () -> Unit,
    onToggleDropIn: (EventInstance) -> Unit,
    onCancelLesson: (EventInstance) -> Unit,
) {
    val router = Router.current
    val currentStrings by strings

    div(className = "card bg-base-100 shadow-sm") {
        div(className = "card-body") {
            div(className = "flex items-center justify-between mb-4") {
                h2(className = "card-title text-lg") {
                    span(className = "icon-[heroicons--calendar-days] size-5 text-secondary")
                    +currentStrings.seriesLessonsHeading
                }
                button(className = "btn btn-primary btn-sm gap-2") {
                    onClick { onAddLesson() }
                    span(className = "icon-[heroicons--plus] size-4")
                    +currentStrings.addLessonButton
                }
            }
            if (lessons == null) {
                div(className = "flex justify-center py-4") {
                    span(className = "loading loading-spinner loading-md")
                }
            } else if (lessons.isEmpty()) {
                p(className = "text-base-content/50 italic text-sm") { +currentStrings.noLessonsYet }
            } else {
                div(className = "overflow-x-auto") {
                    table(className = "table table-sm w-full") {
                        thead {
                            tr {
                                th { +currentStrings.tableHeaderDate }
                                th { +currentStrings.tableHeaderTime }
                                th { +currentStrings.occupancyStatTitle }
                                th { +currentStrings.status }
                                th { +currentStrings.lessonIndividualLabel }
                                th(className = "text-right") { +currentStrings.tableHeaderActions }
                            }
                        }
                        tbody {
                            lessons.forEach { lesson ->
                                LessonRow(
                                    lesson = lesson,
                                    isTogglingDropIn = togglingDropInId == lesson.id,
                                    onToggleDropIn = { onToggleDropIn(lesson) },
                                    onOpenDetail = { router.navigate("/admin/events/instance/${lesson.id}") },
                                    onEdit = { router.navigate("/admin/events/instance/${lesson.id}/edit") },
                                    onCancel = { onCancelLesson(lesson) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IComponent.LessonRow(
    lesson: EventInstance,
    isTogglingDropIn: Boolean,
    onToggleDropIn: () -> Unit,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    val currentStrings by strings

    tr {
        td(className = "font-medium") { +lesson.startDateTime.date.humanReadable }
        td { +"${lesson.startDateTime.hour}:${lesson.startDateTime.minute.toString().padStart(2, '0')} – ${lesson.endDateTime.hour}:${lesson.endDateTime.minute.toString().padStart(2, '0')}" }
        td {
            val isFull = lesson.occupiedSpots >= lesson.capacity
            div(className = "flex items-center gap-2") {
                span(className = if (isFull && !lesson.isCancelled) "text-error font-bold" else "") {
                    +"${lesson.occupiedSpots} / ${lesson.capacity}"
                }
                if (isFull && !lesson.isCancelled) {
                    div(className = "badge badge-error badge-xs") { +currentStrings.capacityFull }
                }
            }
        }
        td {
            if (lesson.isCancelled) {
                div(className = "badge badge-error badge-sm") { +currentStrings.lessonCancelledBadge }
            } else {
                div(className = "badge badge-success badge-sm") { +currentStrings.lessonActiveBadge }
            }
        }
        td {
            if (!lesson.isCancelled) {
                button(className = "btn btn-xs ${if (lesson.isDropIn) "btn-secondary" else "btn-ghost"}") {
                    disabled(isTogglingDropIn)
                    span(className = "icon-[heroicons--globe-alt] size-3")
                    if (isTogglingDropIn) {
                        span(className = "loading loading-spinner loading-xs")
                    } else {
                        if (lesson.isDropIn) +" ${currentStrings.lessonIndividualOn}" else +" ${currentStrings.lessonIndividualOff}"
                    }
                    onClick { onToggleDropIn() }
                }
            }
        }
        td(className = "text-right") {
            div(className = "flex justify-end gap-1") {
                button(className = "btn btn-ghost btn-xs") {
                    title(currentStrings.detail)
                    span(className = "icon-[heroicons--eye] size-4")
                    onClick { onOpenDetail() }
                }
                if (!lesson.isCancelled) {
                    button(className = "btn btn-ghost btn-xs") {
                        span(className = "icon-[heroicons--pencil] size-4")
                        onClick { onEdit() }
                    }
                    button(className = "btn btn-ghost btn-xs text-error") {
                        span(className = "icon-[heroicons--x-circle] size-4")
                        onClick { onCancel() }
                    }
                }
            }
        }
    }
}
