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
import cz.svitaninymburk.projects.reservations.ui.dashboard.DashboardScreen
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.ReservationDetailScreen
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.main
import dev.kilua.routing.browserRouter
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import web.console.console

@Composable
fun IComponent.MainLayout() {
    val authService = getService<AuthServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()

    var currentUser by remember { mutableStateOf<User?>(null) }

    fun refreshUser() = scope.launch {
        authService.getCurrentUser()
            .onRight { currentUser = it }
            .onLeft { error ->
                console.log(error.localizedMessage)
                currentUser = null
            }
    }

    LaunchedEffect(Unit) { refreshUser() }

    div(className = "min-h-screen flex flex-col bg-base-100 text-base-content") {

        AppHeader(
            user = currentUser,
            onLogin = { refreshUser() },
            onLogout = {
                scope.launch {
                    // TODO: authService.logout()
                    currentUser = null
                    refreshUser()
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
                            val router = Router.current
                            ReservationDetailScreen(reservationId = reservationId.value, onBackClick = { router.navigate("/") })
                        }
                    }
                }
            }
        }
    }
}