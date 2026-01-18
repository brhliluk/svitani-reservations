package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.*

@Composable
fun IComponent.DashboardScreen(user: User?, events: List<EventInstance>, onReserve: (EventInstance) -> Unit) {
    val currentStrings by strings
    div(className = "min-h-screen bg-base-200 flex flex-col font-sans") {
        AppHeader(user)

        main(className = "flex-1 w-full max-w-4xl mx-auto py-10 px-4 flex flex-col gap-8") {

            div(className = "flex justify-center") {
                div(className = "join bg-base-100 shadow-sm p-1 rounded-full border border-base-300") {
                    button(className = "btn btn-sm btn-ghost join-item rounded-full bg-base-200 text-base-content font-bold shadow-none hover:bg-base-200") {
                        span(className = "icon-[heroicons--list-bullet] size-5")
                        +currentStrings.listView
                    }
                    button(className = "btn btn-sm btn-ghost join-item rounded-full font-normal text-base-content/60") {
                        span(className = "icon-[heroicons--calendar] size-5")
                        +currentStrings.calendarView
                    }
                }
            }

            div(className = "flex flex-col gap-4") {
                if (events.isEmpty()) {
                    div(className = "alert") {
                        span(className = "icon-[heroicons--information-circle] size-6")
                        +"No events available at the moment."
                    }
                } else {
                    events.forEach {
                        Event(it) { onReserve(it) }
                    }
                }
            }

            div(className = "flex justify-center mt-4") {
                span(className = "loading loading-dots loading-lg text-base-content/30") {}
            }
        }

        footer(className = "footer footer-center p-8 text-base-content/50") {
            aside {
                a(href="#", className = "link link-hover") { +currentStrings.contact }
                p { +"© 2024 Reservation System" }
            }
        }
    }
}