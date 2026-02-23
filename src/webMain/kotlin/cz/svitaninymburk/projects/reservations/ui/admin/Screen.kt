package cz.svitaninymburk.projects.reservations.ui.admin

import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.softwork.routingcompose.Router
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox

@Composable
fun IComponent.AdminLayout(
    user: User,
    onLogout: () -> Unit,
    content: @Composable IComponent.() -> Unit
) {
    val currentStrings by strings
    val router = Router.current

    // DaisyUI Drawer (Boční panel pro navigaci)
    div(className = "drawer lg:drawer-open font-sans bg-base-200 min-h-screen") {
        // Skrytý checkbox, který řídí vysouvání menu na mobilu
        checkBox(className = "drawer-toggle", id = "admin-drawer")

        // --- HLAVNÍ OBSAHOVÁ ČÁST ---
        div(className = "drawer-content flex flex-col") {

            // Horní lišta (Navbar) - Viditelná hlavně na mobilu, na desktopu spíš pro profil
            div(className = "w-full navbar bg-base-100 shadow-sm lg:hidden") {
                div(className = "flex-none") {
                    label(htmlFor = "admin-drawer", className = "btn btn-square btn-ghost") {
                        span(className = "icon-[heroicons--bars-3] size-6")
                    }
                }
                div(className = "flex-1 px-2 mx-2 font-bold text-lg text-primary") {
                    +"Administrace"
                }
            }

            // Samotný obsah obrazovky (zde se bude vykreslovat Router)
            main(className = "flex-1 p-6 w-full max-w-7xl mx-auto") {
                content()
            }
        }

        // --- BOČNÍ PANEL (SIDEBAR) ---
        div(className = "drawer-side z-50") {
            label(htmlFor = "admin-drawer", className = "drawer-overlay")

            div(className = "menu p-4 w-72 min-h-full bg-base-100 text-base-content border-r border-base-200 flex flex-col") {

                // Hlavička sidebaru
                div(className = "flex items-center gap-3 px-4 py-6 mb-4") {
                    span(className = "icon-[heroicons--shield-check] size-8 text-primary")
                    div(className = "font-bold text-xl tracking-wide") { +"Admin Panel" }
                }

                // Navigační linky
                ul(className = "flex-1 space-y-2") {
                    li {
                        a(className = "rounded-lg hover:bg-base-200 transition-colors") {
                            onClick { router.navigate("/admin") }
                            span(className = "icon-[heroicons--home] size-5 text-primary")
                            +"Přehled"
                        }
                    }
                    li {
                        a(className = "rounded-lg hover:bg-base-200 transition-colors") {
                            onClick { router.navigate("/admin/events") }
                            span(className = "icon-[heroicons--calendar-days] size-5 text-info")
                            +"Události a Kurzy"
                        }
                    }
                    li {
                        a(className = "rounded-lg hover:bg-base-200 transition-colors") {
                            onClick { router.navigate("/admin/reservations") }
                            span(className = "icon-[heroicons--ticket] size-5 text-success")
                            +"Rezervace"
                        }
                    }
                    li {
                        a(className = "rounded-lg hover:bg-base-200 transition-colors") {
                            onClick { router.navigate("/admin/users") }
                            span(className = "icon-[heroicons--users] size-5 text-warning")
                            +"Uživatelé"
                        }
                    }
                }

                // Uživatelský profil a odhlášení dole v sidebaru
                div(className = "mt-auto pt-6 border-t border-base-200") {
                    div(className = "flex items-center gap-3 px-2 mb-4") {
                        div(className = "avatar placeholder") {
                            div(className = "bg-primary text-primary-content rounded-full w-10 flex items-center justify-center text-lg") {
                                span { +"${user.name.firstOrNull() ?: ""}${user.surname.firstOrNull() ?: ""}" }
                            }
                        }
                        div(className = "flex flex-col overflow-hidden") {
                            span(className = "text-sm font-bold truncate") { +"${user.name} ${user.surname}" }
                            span(className = "text-xs text-base-content/60 truncate") { +user.email }
                        }
                    }
                    button(className = "btn btn-outline btn-error btn-sm w-full gap-2") {
                        onClick { onLogout() }
                        span(className = "icon-[heroicons--arrow-right-on-rectangle] size-4")
                        +"Odhlásit se"
                    }
                }
            }
        }
    }
}