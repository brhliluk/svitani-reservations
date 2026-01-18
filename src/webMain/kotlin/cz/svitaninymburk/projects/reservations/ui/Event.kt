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

@Composable
fun IComponent.Event(event: EventInstance, onClick: () -> Unit) {
    val currentStrings by strings

    div(className = "card card-bordered bg-base-100 shadow-sm w-full transition-all hover:shadow-md") {
        div(className = "card-body p-6") {

            // Header
            div {
                h3(className = "card-title text-lg font-bold") {
                    +event.title
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

            div(className = "card-actions justify-end mt-2") {
                button(className = "btn btn-neutral btn-sm rounded-full px-6") {
                    +currentStrings.reserve
                    onClick { onClick() }
                }
            }
        }
    }
}