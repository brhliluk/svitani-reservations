package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.AdminDashboardScreen
import cz.svitaninymburk.projects.reservations.ui.admin.AdminLayout
import cz.svitaninymburk.projects.reservations.ui.auth.ResetPasswordScreen
import cz.svitaninymburk.projects.reservations.ui.dashboard.DashboardScreen
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.ReservationDetailScreen
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.main
import dev.kilua.routing.browserRouter
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import web.console.console
import kotlin.uuid.Uuid

@Composable
fun IComponent.MainLayout() {
    val authService = getService<AuthServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()

    var currentUser by remember { mutableStateOf<User?>(null) }
    var toastState by remember { mutableStateOf<ToastData?>(null) }

    fun showToast(message: String, type: ToastType = ToastType.Success) {
        toastState = ToastData(message, type)
    }

    fun refreshUser() = scope.launch {
        authService.getCurrentUser()
            .onRight { currentUser = it }
            .onLeft { error ->
                console.log(error.localizedMessage)
                currentUser = null
            }
    }

    LaunchedEffect(Unit) { refreshUser() }

    if (currentUser?.role == User.Role.ADMIN) {
        // ADMIN VIDÍ ADMIN LAYOUT
        browserRouter {
            route("/admin") {
                view {
                    AdminLayout(
                        user = currentUser!!,
                        onLogout = { scope.launch { authService.logout(); currentUser = null } }
                    ) {
                        AdminDashboardScreen()
                    }
                }
                route("/events") {
                    view { div { +"Zde bude správa událostí" } }
                }
            }
            route("/") {
                view {
                    val router = Router.current
                    LaunchedEffect(Unit) { router.navigate("/admin") }
                }
            }
        }

    } else div(className = "min-h-screen flex flex-col bg-base-100 text-base-content") {

        Toast(
            message = toastState?.message,
            type = toastState?.type ?: ToastType.Success,
            onDismiss = { toastState = null }
        )

        AppHeader(
            user = currentUser,
            onShowMessage = ::showToast,
            onLogin = { refreshUser() },
            onLogout = {
                scope.launch {
                    authService.logout()
                    currentUser = null
                }
            }
        )

        main(className = "flex-grow") {
            browserRouter {
                route("/") {
                    view { DashboardScreen(user = currentUser, initialFilterId = null) }
                }

                route("/reservation") {
                    string { reservationId ->
                        view {
                            val reservationUuid =
                                try { Uuid.parse(reservationId.value) }
                                catch (_: IllegalArgumentException) { null }
                            val router = Router.current
                            if (reservationUuid == null) LaunchedEffect(Unit) { router.navigate("/") }
                            else ReservationDetailScreen(reservationId = reservationUuid, onBackClick = { router.navigate("/") })
                        }
                    }
                }

                route("/reset-password") {
                    string { token ->
                        view {
                            val router = Router.current
                            ResetPasswordScreen(token = token.value, onSuccess = { router.navigate("/") })
                        }
                    }
                }
            }
        }
    }
}