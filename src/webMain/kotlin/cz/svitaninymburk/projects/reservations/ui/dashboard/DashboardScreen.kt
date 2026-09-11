package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.span

@Composable
fun IComponent.DashboardScreen(
    user: User?,
    walletCode: String? = null,
    initialFilterId: String? = null,
    initialSeriesId: String? = null,
) {
    val currentStrings by strings
    val router = Router.current
    val scope = rememberCoroutineScope()
    val model = remember { buildDashboardModel(scope, router) }

    LaunchedEffect(Unit) { model.load() }

    when (val state = model.uiState) {
        is DashboardUiState.Loading -> Loading()
        is DashboardUiState.Success -> DashboardLayout(
            user = user,
            walletCode = walletCode,
            events = state.instances,
            series = state.series,
            definitions = state.definitions,
            initialFilterId = initialFilterId,
            initialSeriesId = initialSeriesId,
            isSubmitting = model.isSubmitting,
            onSubmitReservation = { target, formData -> model.submitReservation(target, formData, user?.id) },
            onFilterChange = { id -> router.navigate(if (id == null) "/" else "/?filter=$id") },
            onSeriesFilterChange = { id -> router.navigate(if (id == null) "/" else "/?series=$id") },
        )

        is DashboardUiState.Error -> {
            div(className = "min-h-screen flex items-center justify-center bg-base-200") {
                div(className = "alert alert-error max-w-md") {
                    span(className = "icon-[heroicons--exclamation-circle] size-6")
                    span { +state.message }
                    button(className = "btn min-h-11") {
                        onClick { model.load() }
                        +currentStrings.retry
                    }
                }
            }
        }
    }

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Error,
        onDismiss = { model.dismissToast() },
    )
}
