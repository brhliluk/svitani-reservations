package cz.svitaninymburk.projects.reservations.ui.admin

import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.auth.ChangePasswordDialog
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    val scope = rememberCoroutineScope()
    val model = remember { AdminLayoutModel(scope) }

    // DaisyUI Drawer (Boční panel pro navigaci)
    div(className = "drawer lg:drawer-open font-sans bg-base-200 min-h-screen") {
        ChangePasswordDialog(
            isOpen = model.showChangePassword,
            onClose = { model.closeChangePassword() },
            onSuccess = { model.onPasswordChanged() },
        )

        Toast(
            message = model.toast?.message,
            type = model.toast?.type ?: ToastType.Success,
            onDismiss = { model.dismissToast() },
        )
        // Skrytý checkbox, který řídí vysouvání menu na mobilu
        checkBox(className = "drawer-toggle", id = "admin-drawer")

        // --- HLAVNÍ OBSAHOVÁ ČÁST ---
        div(className = "drawer-content flex flex-col") {

            // Horní lišta (Navbar) - Viditelná hlavně na mobilu, na desktopu spíš pro profil
            div(className = "w-full navbar bg-base-100 shadow-sm lg:hidden print:hidden") {
                div(className = "flex-none") {
                    label(htmlFor = "admin-drawer", className = "btn btn-square btn-ghost") {
                        attribute("aria-label", currentStrings.ariaOpenMenu)
                        span(className = "icon-[heroicons--bars-3] size-6")
                    }
                }
                div(className = "flex-1 px-2 mx-2 font-bold text-lg text-primary") {
                    +currentStrings.adminNavTitle
                }
            }

            // Samotný obsah obrazovky (zde se bude vykreslovat Router)
            main(className = "flex-1 p-6 w-full max-w-7xl mx-auto") {
                content()
            }
        }

        // --- BOČNÍ PANEL (SIDEBAR) ---
        div(className = "drawer-side z-50 print:hidden") {
            label(htmlFor = "admin-drawer", className = "drawer-overlay")

            div(className = "menu p-4 w-72 min-h-full bg-base-100 text-base-content border-r border-base-200 flex flex-col") {

                // Hlavička sidebaru
                div(className = "flex items-center gap-3 px-4 py-6 mb-4") {
                    span(className = "icon-[heroicons--shield-check] size-8 text-primary")
                    div(className = "font-bold text-xl tracking-wide") { +currentStrings.adminPanel }
                }

                AdminNavLinks { router.navigate(it) }

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
                    if (user is User.Email) {
                        button(className = "btn btn-ghost btn-sm w-full gap-2 mb-2 justify-start") {
                            onClick { model.openChangePassword() }
                            span(className = "icon-[heroicons--key] size-4")
                            +currentStrings.changePassword
                        }
                    }
                    button(className = "btn btn-outline btn-error btn-sm w-full gap-2") {
                        onClick { onLogout() }
                        span(className = "icon-[heroicons--arrow-right-on-rectangle] size-4")
                        +currentStrings.logOut
                    }
                }
            }
        }
    }
}

/**
 * Odkazy v bočním panelu. Ikony jsou celé řetězce schválně — Tailwind si třídy
 * vytahuje ze zdrojáků, poskládané po kouscích by se do CSS nedostaly.
 */
@Composable
private fun IComponent.AdminNavLinks(onNavigate: (String) -> Unit) {
    val currentStrings by strings
    val links = listOf(
        Triple("/admin", "icon-[heroicons--home] size-5 text-primary/70", currentStrings.dashboard),
        Triple("/admin/schedule", "icon-[heroicons--clock] size-5 text-primary/70", currentStrings.navSchedule),
        Triple("/admin/events", "icon-[heroicons--calendar-days] size-5 text-primary/70", currentStrings.navEvents),
        Triple("/admin/reservations", "icon-[heroicons--ticket] size-5 text-primary/70", currentStrings.navReservations),
        Triple("/admin/users", "icon-[heroicons--users] size-5 text-primary/70", currentStrings.navUsers),
        Triple("/admin/payments", "icon-[heroicons--banknotes] size-5 text-primary/70", currentStrings.navPayments),
        Triple("/admin/wallets", "icon-[heroicons--wallet] size-5 text-primary/70", currentStrings.adminWallets),
        Triple("/admin/settings", "icon-[heroicons--cog-6-tooth] size-5 text-primary/70", currentStrings.navSettings),
    )

    ul(className = "flex-1 space-y-2") {
        links.forEach { (route, icon, label) ->
            li {
                a(className = "rounded-lg hover:bg-base-200 transition-colors") {
                    onClick { onNavigate(route) }
                    span(className = icon)
                    +label
                }
            }
        }
        // Oddělený odkaz ven z administrace — proto vlastní li s linkou nahoře.
        li(className = "mt-2 pt-2 border-t border-base-200") {
            a(className = "rounded-lg hover:bg-base-200 transition-colors") {
                onClick { onNavigate("/") }
                span(className = "icon-[heroicons--eye] size-5 text-primary/70")
                +currentStrings.viewAsUser
            }
        }
    }
}
