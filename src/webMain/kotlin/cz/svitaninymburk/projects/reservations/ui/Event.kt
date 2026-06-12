package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.copyToClipboard
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.p
import dev.kilua.html.span
import dev.kilua.html.progress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import web.window.window
import kotlin.time.Duration.Companion.seconds

@Composable
fun IComponent.Event(event: EventInstance, onClick: () -> Unit) {
    val currentStrings by strings
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    val progressClass = if (event.isFull) "progress-error"
        else if (event.occupiedSpots.toDouble() / event.capacity > 0.8) "progress-warning"
        else "progress-success"

    div(className = "card card-bordered bg-base-100 shadow-sm w-full transition-shadow hover:shadow-md") {
        div(className = "card-body p-4 sm:p-6 gap-2") {

            // Header
            div {
                div(className = "flex items-start justify-between gap-2") {
                    h3(className = "card-title text-base sm:text-lg font-bold") { +event.title }
                    div(className = "flex items-center gap-1 shrink-0") {
                        button(className = "btn btn-ghost btn-xs btn-square tooltip tooltip-left ${if (isCopied) "text-success" else "text-base-content/40 hover:text-base-content"}") {
                            attribute("aria-label", currentStrings.copyEventLink)
                            attribute("data-tip", if (isCopied) currentStrings.copied else currentStrings.copyEventLink)
                            onClick {
                                val url = "${window.location.origin}/?filter=${event.definitionId}"
                                copyToClipboard(url)
                                isCopied = true
                                scope.launch {
                                    delay(2.seconds)
                                    isCopied = false
                                }
                            }
                            span(className = if (isCopied) "icon-[heroicons--check] size-4" else "icon-[heroicons--link] size-4")
                        }
                        if (event.isCancelled) {
                            div(className = "badge badge-error badge-sm") { +currentStrings.cancelled }
                        }
                    }
                }
                if (event.seriesId != null) {
                    div(className = "badge badge-secondary badge-sm gap-1 mb-1") {
                        span(className = "icon-[heroicons--academic-cap] size-3")
                        +currentStrings.partOfCourse
                    }
                }
                div(className = "flex items-center gap-2 text-sm text-base-content/60 mt-1") {
                    span(className = "icon-[heroicons--clock] size-4")
                    span {
                        +event.startDateTime.humanReadable
                        +" - "
                        +event.endDateTime.humanReadable
                    }
                }
            }

            p(className = "py-2 text-base-content/80 text-sm leading-relaxed") {
                +event.description
            }

            // Capacity progress bar — only shown when attendee count is visible
            if (event.showAttendeeCount) {
                progress(className = "progress $progressClass w-full h-2") {
                    attribute("value", event.occupiedSpots.toString())
                    attribute("max", event.capacity.toString())
                    attribute("aria-label", currentStrings.ariaCapacityProgress(event.occupiedSpots, event.capacity))
                }
            }

            div(className = "card-actions items-center justify-between mt-3 pt-3 border-t border-base-200") {
                // Left: price + capacity
                div(className = "flex flex-col gap-1") {
                    val priceText = if (event.price == 0.0) currentStrings.free
                        else "${event.price} ${currentStrings.currency}"
                    span(className = "text-lg font-bold text-primary") { +priceText }
                    if (event.isFull) {
                        // Always show Full badge regardless of showAttendeeCount
                        div(className = "badge badge-error badge-sm font-bold") { +currentStrings.capacityFull }
                    } else if (event.showAttendeeCount) {
                        span(className = "text-xs font-bold text-base-content/60") {
                            +"${event.occupiedSpots} / ${event.capacity}"
                        }
                    }
                }
                // Right: reserve button or deadline message
                val isDisabled = event.isCancelled || event.isFull || event.isDeadlinePassed
                div(className = "flex flex-col items-end gap-1") {
                    button(className = "btn btn-primary rounded-full px-6 min-h-11${if (isDisabled) " btn-disabled" else ""}") {
                        +currentStrings.reserve
                        if (!isDisabled) onClick { onClick() }
                    }
                    if (event.isDeadlinePassed) {
                        span(className = "text-xs text-base-content/60 text-right") {
                            +(event.reservationDeadlineMessage ?: currentStrings.reservationClosed)
                        }
                    }
                }
            }
        }
    }
}