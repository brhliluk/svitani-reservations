package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun IComponent.Event(event: EventInstance, onClick: () -> Unit) {
    val currentStrings by strings
    val progressClass = if (event.isFull) "progress-error"
        else if (event.occupiedSpots.toDouble() / event.capacity > 0.8) "progress-warning"
        else "progress-success"

    div(className = "card card-bordered bg-base-100 shadow-sm w-full transition-all hover:shadow-md") {
        div(className = "card-body p-4 sm:p-6 gap-2") {

            // Header
            div {
                div(className = "flex items-start justify-between gap-2") {
                    h3(className = "card-title text-base sm:text-lg font-bold") { +event.title }
                    if (event.isCancelled) {
                        div(className = "badge badge-error badge-sm shrink-0") { +currentStrings.cancelled }
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

            // Capacity progress bar
            progress(className = "progress $progressClass w-full h-2") {
                attribute("value", event.occupiedSpots.toString())
                attribute("max", event.capacity.toString())
            }

            div(className = "card-actions items-center justify-between mt-3 pt-3 border-t border-base-200") {
                // Left: price + capacity
                div(className = "flex flex-col gap-1") {
                    val priceText = if (event.price == 0.0) currentStrings.free
                        else "${event.price} ${currentStrings.currency}"
                    span(className = "text-lg font-bold text-primary") { +priceText }
                    if (event.isFull) {
                        div(className = "badge badge-error badge-sm font-bold") { +currentStrings.capacityFull }
                    } else {
                        span(className = "text-xs font-bold text-base-content/60") {
                            +"${event.occupiedSpots} / ${event.capacity}"
                        }
                    }
                }
                // Right: reserve button
                val isDisabled = event.isCancelled || event.isFull
                button(className = "btn btn-neutral rounded-full px-6 min-h-11${if (isDisabled) " btn-disabled" else ""}") {
                    +currentStrings.reserve
                    if (!isDisabled) onClick { onClick() }
                }
            }
        }
    }
}