package cz.svitaninymburk.projects.reservations.ui

import cz.svitaninymburk.projects.reservations.LocalWebFeatures
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.HeaderLink
import cz.svitaninymburk.projects.reservations.PageContext
import cz.svitaninymburk.projects.reservations.WebFeature
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
import dev.kilua.routing.RoutingBuilder
import dev.kilua.routing.browserRouter
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/** [features] jsou volitelné moduly, které do aplikace přidal kořenový projekt. */
@Composable
fun IComponent.MainLayout(features: List<WebFeature> = emptyList()) {
    val scope = rememberCoroutineScope()
    val session = remember { buildSessionModel(scope) }
    val headerLinks = visibleHeaderLinks(features, session)

    LaunchedEffect(Unit) { session.refresh() }

    // Admin menu je hluboko v layoutu; moduly se k němu dostanou přes CompositionLocal.
    CompositionLocalProvider(LocalWebFeatures provides features) {
        MainRoutes(session, features, headerLinks)
    }
}

@Composable
private fun IComponent.MainRoutes(session: SessionModel, features: List<WebFeature>, headerLinks: List<HeaderLink>) {
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
                route("/reservations") { context ->
                    view {
                        AdminRoute(session) {
                            AdminReservationsScreen(initialSearch = context.parameters?.map?.get("search")?.firstOrNull())
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
                features.flatMap { it.adminPages }.forEach { page ->
                    route(page.path) {
                        page.detail?.let { detail ->
                            string {
                                // Hodnotu brát z parametru view, ne z kontextu (`it.value`): kontext je obyčejné
                                // pole mimo Compose, takže při přechodu /x/new → /x/<id> by se view přeskočil
                                // a zůstala by stará obrazovka. key(id) dá každé adrese čerstvý stav obrazovky.
                                view { id ->
                                    AdminRoute(session) {
                                        key(id) {
                                            detail(this, PageContext(session.currentUser, session.walletCode), id)
                                        }
                                    }
                                }
                            }
                        }
                        view {
                            AdminRoute(session) {
                                page.content(this, PageContext(session.currentUser, session.walletCode))
                            }
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
            publicRoutes(session, features, headerLinks)
        }

    } else {
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
            publicRoutes(session, features, headerLinks)
        }
    }
}

/**
 * Veřejné stránky — stejné pro admina i ostatní, liší se jen tím, co je před
 * nimi v routeru. Poslední `view` přesměruje neznámou adresu na přehled.
 */
private fun RoutingBuilder.publicRoutes(
    session: SessionModel,
    features: List<WebFeature>,
    headerLinks: List<HeaderLink>,
) {
    route("/") { context ->
        view {
            UserRoute(session, headerLinks) {
                DashboardScreen(
                    user = session.currentUser,
                    walletCode = session.walletCode,
                    initialFilterId = context.parameters?.map?.get("filter")?.firstOrNull(),
                    initialSeriesId = context.parameters?.map?.get("series")?.firstOrNull(),
                    initialInstanceId = context.parameters?.map?.get("event")?.firstOrNull(),
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
                UserRoute(session, headerLinks) {
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
            UserRoute(session, headerLinks) {
                if (user == null) LaunchedEffect(Unit) { router.navigate("/") }
                else MyReservationsScreen(userId = user.id, onBackClick = { router.navigate("/") })
            }
        }
    }

    route("/wallet") {
        string { code ->
            view {
                UserRoute(session, headerLinks) {
                    WalletScreen(initialCode = code.value, initialEmail = session.currentUser?.email ?: "")
                }
            }
        }
        view {
            UserRoute(session, headerLinks) {
                WalletScreen()
            }
        }
    }

    route("/reset-password") {
        string { token ->
            view {
                val router = Router.current
                UserRoute(session, headerLinks) {
                    ResetPasswordScreen(token = token.value, onSuccess = { router.navigate("/") })
                }
            }
        }
    }
    route("/claim-reservations") {
        string { token ->
            view {
                val router = Router.current
                UserRoute(session, headerLinks) {
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
            UserRoute(session, headerLinks) {
                PrivacyScreen()
            }
        }
    }
    features.flatMap { it.publicPages }.forEach { page ->
        route(page.path) {
            view {
                UserRoute(session, headerLinks) {
                    page.content(this, PageContext(user = session.currentUser, walletCode = session.walletCode))
                }
            }
        }
    }
    view {
        val router = Router.current
        LaunchedEffect(Unit) { router.navigate("/") }
    }
}

/**
 * Odkazy modulů do hlavičky, které se mají ukázat. Ptá se až po načtení session
 * a znovu po každém přihlášení nebo odhlášení — co modul nabízí, může záviset na roli.
 */
@Composable
private fun visibleHeaderLinks(features: List<WebFeature>, session: SessionModel): List<HeaderLink> {
    var visible by remember { mutableStateOf(emptyList<HeaderLink>()) }
    LaunchedEffect(features, session.isLoaded, session.currentUser?.id) {
        if (!session.isLoaded) return@LaunchedEffect
        visible = features.flatMap { it.headerLinks }.filter { link ->
            try {
                link.isVisible()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }
    return visible
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
private fun IComponent.UserRoute(
    session: SessionModel,
    headerLinks: List<HeaderLink>,
    content: @Composable IComponent.() -> Unit,
) {
    val router = Router.current
    UserShell(
        user = session.currentUser,
        headerLinks = headerLinks,
        onNavigate = { router.navigate(it) },
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
    headerLinks: List<HeaderLink>,
    onNavigate: (String) -> Unit,
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
    // Obal patří k shellu, ne k větvi routeru: bez flex sloupce nemá main co
    // roztáhnout a patička by u krátké stránky visela uprostřed.
    div(className = "min-h-screen flex flex-col bg-base-100 text-base-content") {
        AppHeader(
            user = user,
            headerLinks = headerLinks,
            onNavigate = onNavigate,
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
}