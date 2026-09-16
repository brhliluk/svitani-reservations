package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.AdminDashboardScreen
import cz.svitaninymburk.projects.reservations.ui.admin.AdminLayout
import cz.svitaninymburk.projects.reservations.ui.admin.events.AdminAttendanceScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.AdminCreateEventScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.AdminCreateEventDefinitionScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.AdminEditEventDefinitionScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.AdminCreateEventInstanceScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.AdminEditEventInstanceScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.AdminCreateEventSeriesScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.AdminEditEventSeriesScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.AdminEventCreateChooseScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.AdminEventDetailScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.EventPreviewScreen
import cz.svitaninymburk.projects.reservations.ui.admin.events.AdminEventsScreen
import cz.svitaninymburk.projects.reservations.ui.admin.schedule.AdminScheduleScreen
import cz.svitaninymburk.projects.reservations.ui.admin.payments.AdminPaymentsScreen
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.AdminReservationsScreen
import cz.svitaninymburk.projects.reservations.ui.admin.settings.AdminSettingsScreen
import cz.svitaninymburk.projects.reservations.ui.admin.users.AdminUsersScreen
import cz.svitaninymburk.projects.reservations.ui.admin.wallet.AdminWalletsScreen
import cz.svitaninymburk.projects.reservations.ui.auth.ResetPasswordScreen
import cz.svitaninymburk.projects.reservations.ui.claim.ClaimOfferDialog
import cz.svitaninymburk.projects.reservations.ui.claim.ClaimReservationsScreen
import cz.svitaninymburk.projects.reservations.ui.dashboard.DashboardScreen
import cz.svitaninymburk.projects.reservations.ui.dashboard.MyReservationsScreen
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.ReservationDetailScreen
import cz.svitaninymburk.projects.reservations.ui.wallet.WalletScreen
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.a
import dev.kilua.html.aside
import dev.kilua.html.div
import dev.kilua.html.footer
import dev.kilua.html.main
import dev.kilua.html.p
import dev.kilua.routing.browserRouter
import kotlin.uuid.Uuid

@Composable
fun IComponent.MainLayout() {
    val scope = rememberCoroutineScope()
    val session = remember { buildSessionModel(scope) }

    LaunchedEffect(Unit) { session.refresh() }

    if (!session.isLoaded) {
        div {}
    } else if (session.isAdmin) {
        // ADMIN VIDÍ ADMIN LAYOUT
        Toast(
            message = session.toast?.message,
            type = session.toast?.type ?: ToastType.Success,
            onDismiss = { session.dismissToast() },
        )

        ClaimOfferDialog(session)
        browserRouter {
            route("/admin") {
                view {
                    AdminRoute(session) {
                        AdminDashboardScreen()
                    }
                }
                route("/schedule") {
                    view {
                        AdminRoute(session) {
                            AdminScheduleScreen()
                        }
                    }
                }
                route("/events") {
                    view {
                        AdminRoute(session) {
                            AdminEventsScreen()
                        }
                    }
                    route("/instance") { string { eventId ->
                        view {
                            AdminRoute(session) {
                                AdminEventDetailScreen(eventId = eventId.value, isSeries = false)
                            }
                        }
                        route("/attendance") {
                            view {
                                AdminRoute(session) {
                                    AdminAttendanceScreen(eventId = eventId.value, isSeries = false)
                                }
                            }
                        }
                        route("/edit") {
                            view {
                                AdminRoute(session) {
                                    AdminEditEventInstanceScreen(id = eventId.value)
                                }
                            }
                        }
                        route("/preview") {
                            view {
                                AdminRoute(session) {
                                    EventPreviewScreen(eventId = eventId.value, isSeries = false, currentUser = session.currentUser!!)
                                }
                            }
                        }
                    } }
                    route("/series") { string { seriesId ->
                        view {
                            AdminRoute(session) {
                                AdminEventDetailScreen(eventId = seriesId.value, isSeries = true)
                            }
                        }
                        route("/attendance") {
                            view {
                                AdminRoute(session) {
                                    AdminAttendanceScreen(eventId = seriesId.value, isSeries = true)
                                }
                            }
                        }
                        route("/edit") {
                            view {
                                AdminRoute(session) {
                                    AdminEditEventSeriesScreen(id = seriesId.value)
                                }
                            }
                        }
                        route("/preview") {
                            view {
                                AdminRoute(session) {
                                    EventPreviewScreen(eventId = seriesId.value, isSeries = true, currentUser = session.currentUser!!)
                                }
                            }
                        }
                    } }
                    route("/new") {
                        view {
                            AdminRoute(session) {
                                AdminCreateEventScreen(currentUser = session.currentUser!!)
                            }
                        }
                    }
                    route("/create") {
                        route("/definition") {
                            view {
                                AdminRoute(session) {
                                    AdminCreateEventDefinitionScreen(currentUser = session.currentUser!!)
                                }
                            }
                        }
                        route("/choose") {
                            string { definitionId ->
                                view {
                                    AdminRoute(session) {
                                        AdminEventCreateChooseScreen(definitionId = definitionId.value)
                                    }
                                }
                            }
                        }
                        route("/instance") {
                            view {
                                AdminRoute(session) {
                                    AdminCreateEventInstanceScreen(currentUser = session.currentUser!!)
                                }
                            }
                            string { definitionId ->
                                view {
                                    AdminRoute(session) {
                                        AdminCreateEventInstanceScreen(currentUser = session.currentUser!!, preselectedDefinitionId = definitionId.value)
                                    }
                                }
                            }
                        }
                        route("/series") {
                            view {
                                AdminRoute(session) {
                                    AdminCreateEventSeriesScreen(currentUser = session.currentUser!!)
                                }
                            }
                            string { definitionId ->
                                view {
                                    AdminRoute(session) {
                                        AdminCreateEventSeriesScreen(currentUser = session.currentUser!!, preselectedDefinitionId = definitionId.value)
                                    }
                                }
                            }
                        }
                    }
                    route("/definition") {
                        string { definitionId ->
                            route("/edit") {
                                view {
                                    AdminRoute(session) {
                                        AdminEditEventDefinitionScreen(id = definitionId.value)
                                    }
                                }
                            }
                        }
                    }
                }
                route("/reservations") {
                    view {
                        AdminRoute(session) {
                            AdminReservationsScreen()
                        }
                    }
                }
                route("/users") {
                    view {
                        AdminRoute(session) {
                            AdminUsersScreen(currentUserId = session.currentUser!!.id)
                        }
                    }
                }
                route("/settings") {
                    view {
                        AdminRoute(session) {
                            AdminSettingsScreen()
                        }
                    }
                }
                route("/payments") {
                    view {
                        AdminRoute(session) {
                            AdminPaymentsScreen()
                        }
                    }
                }
                route("/wallets") {
                    // Proklik z historie: /admin/wallets/{kód} otevře rovnou detail peněženky.
                    string { walletCode ->
                        view {
                            AdminRoute(session) {
                                AdminWalletsScreen(preselectCode = walletCode.value)
                            }
                        }
                    }
                    view {
                        AdminRoute(session) {
                            AdminWalletsScreen()
                        }
                    }
                }
            }
            route("/reservation") {
                string { reservationId ->
                    view {
                        val reservationUuid =
                            try { Uuid.parse(reservationId.value) }
                            catch (_: IllegalArgumentException) { null }
                        val router = Router.current
                        UserRoute(session) {
                            if (reservationUuid == null) LaunchedEffect(Unit) { router.navigate("/") }
                            else ReservationDetailScreen(reservationId = reservationUuid, onBackClick = { router.navigate("/") })
                        }
                    }
                }
            }
            route("/my-reservations") {
                view {
                    val router = Router.current
                    UserRoute(session) {
                        MyReservationsScreen(userId = session.currentUser!!.id, onBackClick = { router.navigate("/") })
                    }
                }
            }
            route("/wallet") {
                string { code ->
                    view {
                        UserRoute(session) {
                            WalletScreen(initialCode = code.value, initialEmail = session.currentUser?.email ?: "")
                        }
                    }
                }
                view {
                    UserRoute(session) {
                        WalletScreen()
                    }
                }
            }
            route("/reset-password") {
                string { token ->
                    view {
                        val router = Router.current
                        UserRoute(session) {
                            ResetPasswordScreen(token = token.value, onSuccess = { router.navigate("/") })
                        }
                    }
                }
            }
            route("/claim-reservations") {
                string { token ->
                    view {
                        val router = Router.current
                        UserRoute(session) {
                            ClaimReservationsScreen(
                                token = token.value,
                                onOpenMyReservations = { router.navigate("/my-reservations") },
                            )
                        }
                    }
                }
            }
            route("/privacy") {
                view {
                    UserRoute(session) {
                        PrivacyScreen()
                    }
                }
            }
            route("/") { context ->
                view {
                    UserRoute(session) {
                        DashboardScreen(
                            user = session.currentUser,
                            walletCode = session.walletCode,
                            initialFilterId = context.parameters?.map?.get("filter")?.firstOrNull(),
                            initialSeriesId = context.parameters?.map?.get("series")?.firstOrNull(),
                        )
                    }
                }
            }
            view {
                val router = Router.current
                LaunchedEffect(Unit) { router.navigate("/") }
            }
        }

    } else div(className = "min-h-screen flex flex-col bg-base-100 text-base-content") {

        Toast(
            message = session.toast?.message,
            type = session.toast?.type ?: ToastType.Success,
            onDismiss = { session.dismissToast() },
        )

        ClaimOfferDialog(session)

        browserRouter {
            route("/admin") {
                view {
                    val router = Router.current
                    LaunchedEffect(Unit) { router.navigate("/") }
                }
            }
            route("/") { context ->
                view {
                    UserRoute(session) {
                        DashboardScreen(
                            user = session.currentUser,
                            walletCode = session.walletCode,
                            initialFilterId = context.parameters?.map?.get("filter")?.firstOrNull(),
                            initialSeriesId = context.parameters?.map?.get("series")?.firstOrNull(),
                        )
                    }
                }
            }

            route("/reservation") {
                string { reservationId ->
                    view {
                        val reservationUuid =
                            try { Uuid.parse(reservationId.value) }
                            catch (_: IllegalArgumentException) { null }
                        val router = Router.current
                        UserRoute(session) {
                            if (reservationUuid == null) LaunchedEffect(Unit) { router.navigate("/") }
                            else ReservationDetailScreen(reservationId = reservationUuid, onBackClick = { router.navigate("/") })
                        }
                    }
                }
            }

            route("/my-reservations") {
                view {
                    val router = Router.current
                    val user = session.currentUser
                    UserRoute(session) {
                        if (user == null) LaunchedEffect(Unit) { router.navigate("/") }
                        else MyReservationsScreen(userId = user.id, onBackClick = { router.navigate("/") })
                    }
                }
            }

            route("/wallet") {
                string { code ->
                    view {
                        UserRoute(session) {
                            WalletScreen(initialCode = code.value, initialEmail = session.currentUser?.email ?: "")
                        }
                    }
                }
                view {
                    UserRoute(session) {
                        WalletScreen()
                    }
                }
            }

            route("/reset-password") {
                string { token ->
                    view {
                        val router = Router.current
                        UserRoute(session) {
                            ResetPasswordScreen(token = token.value, onSuccess = { router.navigate("/") })
                        }
                    }
                }
            }
            route("/claim-reservations") {
                string { token ->
                    view {
                        val router = Router.current
                        UserRoute(session) {
                            ClaimReservationsScreen(
                                token = token.value,
                                onOpenMyReservations = { router.navigate("/my-reservations") },
                            )
                        }
                    }
                }
            }
            route("/privacy") {
                view {
                    UserRoute(session) {
                        PrivacyScreen()
                    }
                }
            }
            view {
                val router = Router.current
                LaunchedEffect(Unit) { router.navigate("/") }
            }
        }
    }
}

/**
 * Administrační obrazovka v postranním menu. Do admin větve se routuje jen
 * s přihlášeným adminem, proto je `currentUser` na tomhle místě jistota.
 */
@Composable
private fun IComponent.AdminRoute(session: SessionModel, content: @Composable IComponent.() -> Unit) {
    AdminLayout(user = session.currentUser!!, onLogout = { session.logout() }, content = content)
}

/**
 * Veřejná obrazovka s hlavičkou. Odkaz do administrace se v hlavičce ukáže sám
 * podle role — dřív o tom rozhodovalo to, ze které větve routeru se `UserShell` volal.
 */
@Composable
private fun IComponent.UserRoute(session: SessionModel, content: @Composable IComponent.() -> Unit) {
    val router = Router.current
    UserShell(
        user = session.currentUser,
        walletCode = session.walletCode,
        onShowMessage = session::showMessage,
        onLogin = { session.refresh(); session.checkClaimable() },
        onLogout = { session.logout() },
        onOpenMyReservations = { router.navigate("/my-reservations") },
        onOpenMyWallet = { session.walletCode?.let { router.navigate("/wallet/$it") } },
        onNavigateToDashboard = { router.navigate("/") },
        onNavigateToAdmin = if (session.isAdmin) ({ router.navigate("/admin") }) else null,
        content = content,
    )
}

@Composable
private fun IComponent.UserShell(
    user: User?,
    walletCode: String?,
    onShowMessage: (String, ToastType) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenMyReservations: () -> Unit,
    onOpenMyWallet: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToAdmin: (() -> Unit)? = null,
    content: @Composable IComponent.() -> Unit,
) {
    val currentStrings by strings
    AppHeader(
        user = user,
        walletCode = walletCode,
        onShowMessage = onShowMessage,
        onLogin = onLogin,
        onLogout = onLogout,
        onOpenMyReservations = onOpenMyReservations,
        onOpenMyWallet = onOpenMyWallet,
        onNavigateToDashboard = onNavigateToDashboard,
        onNavigateToAdmin = onNavigateToAdmin,
    )
    main(className = "flex-grow") { content() }
    footer(className = "footer footer-center p-8 text-base-content/50") {
        aside {
            a(href = "#", className = "link link-hover") { +currentStrings.contact }
            a(href = "/privacy", className = "link link-hover") { +currentStrings.privacyPolicyLink }
            p { +currentStrings.copyright }
        }
    }
}