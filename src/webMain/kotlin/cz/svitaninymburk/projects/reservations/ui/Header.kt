package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.a
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.header
import dev.kilua.html.span

@Composable
fun IComponent.AppHeader(user: User?) {
    val currentStrings by strings
    header(className = "navbar bg-base-100 border-b border-base-200 px-4 sm:px-8") {

        div(className = "navbar-start gap-4") {
            div(className = "logo placeholder") {
                div(className = "bg-neutral text-neutral-content rounded-lg w-10 grid place-items-center") {
                    span(className = "text-xl font-bold") { +"B" }
                }
            }
            span(className = "text-lg font-medium text-base-content") { +currentStrings.dashboard }
        }

        div(className = "navbar-end gap-4") {
            a(className = "btn btn-ghost btn-sm hidden sm:inline-flex") {
                href("#")
                +"My reservations"
            }

            div(className = "flex items-center gap-3 pl-4 border-l border-base-200") {
                div(className = "avatar placeholder") {
                    div(className = "bg-primary/10 text-primary w-10 rounded-full grid place-items-center") {
                        span(className = "icon-[heroicons--user] size-6")
                    }
                }

                div(className = "flex flex-col text-sm") {
                    if (user != null) {
                        span(className = "font-semibold text-base-content") {
                            +"${user.name} ${user.surname}"
                        }
                        span(className = "text-xs text-base-content/60") { +user.email }
                    } else {
                        button(className = "btn btn-sm btn-ghost") { +currentStrings.logIn }
                    }
                }
            }
        }
    }
}