package cz.svitaninymburk.projects.reservations.ui.admin

import cz.svitaninymburk.projects.reservations.ui.util.formatAmount
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.usecase.CapacityLevel
import cz.svitaninymburk.projects.reservations.ui.admin.usecase.capacityLevel
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.html.*

@Composable
fun IComponent.AdminDashboardScreen() {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildAdminDashboardModel(scope) }

    LaunchedEffect(Unit) { model.load() }

    when (val state = model.uiState) {
        is AdminDashboardUiState.Loading -> Loading()
        is AdminDashboardUiState.Error -> {
            div(className = "alert alert-error") { +currentStrings.loadingError(state.message) }
        }
        is AdminDashboardUiState.Success -> {
            val data = state.data

            div(className = "flex flex-col gap-8 animate-fade-in") {
                // Hlavička
                div {
                    h1(className = "text-3xl font-bold text-base-content") { +currentStrings.dashboard }
                    p(className = "text-base-content/60 mt-1") { +currentStrings.dashboardWelcome }
                }

                // --- 1. KPI STATISTIKY ---
                div(className = "stats stats-vertical lg:stats-horizontal shadow-sm w-full bg-base-100") {
                    div(className = "stat") {
                        div(className = "stat-figure text-primary") { span(className = "icon-[heroicons--users] size-8") }
                        div(className = "stat-title") { +currentStrings.dashboardTodayParticipants }
                        div(className = "stat-value text-primary") { +"${data.todayParticipantsCount}" }
                    }
                    div(className = "stat") {
                        div(className = "stat-figure text-warning") { span(className = "icon-[heroicons--banknotes] size-8") }
                        div(className = "stat-title") { +currentStrings.dashboardPendingPayment }
                        div(className = "stat-value text-warning") { +formatAmount(data.pendingPaymentsTotal, currentStrings) }
                        div(className = "stat-desc") { +currentStrings.dashboardPendingPaymentsDesc(data.pendingPaymentsCount) }
                    }
                    div(className = "stat") {
                        div(className = "stat-figure text-info") { span(className = "icon-[heroicons--ticket] size-8") }
                        div(className = "stat-title") { +currentStrings.dashboardFreeSpots }
                        div(className = "stat-value text-info") { +"${data.freeSpotsThisWeek}" }
                        div(className = "stat-desc") { +currentStrings.dashboardFreeSpotsDesc }
                    }
                }

                // --- 2. HLAVNÍ OBSAH ---
                div(className = "grid grid-cols-1 lg:grid-cols-2 gap-8") {
                    // LEVÝ SLOUPEC: Události
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body p-6") {
                            div(className = "flex items-center justify-between mb-4") {
                                h2(className = "card-title text-lg") { +currentStrings.dashboardUpcomingEvents }
                                button(className = "btn btn-ghost btn-xs gap-1 text-primary") {
                                    onClick { router.navigate("/admin/schedule") }
                                    +currentStrings.dashboardShowAllEvents
                                    span(className = "icon-[heroicons--arrow-right] size-3")
                                }
                            }
                            div(className = "flex flex-col gap-4") {
                                if (data.upcomingEvents.isEmpty()) {
                                    p(className = "text-sm text-base-content/50 italic") { +currentStrings.dashboardNoUpcomingEvents }
                                } else {
                                    data.upcomingEvents.forEach { event ->
                                        AdminUpcomingEventRow(event.title, event.startDateTime.humanReadable, event.occupiedSpots, event.capacity) {
                                            router.navigate("/admin/events/instance/${event.id}")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // PRAVÝ SLOUPEC: Rezervace
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body p-6") {
                            h2(className = "card-title text-lg mb-4") { +currentStrings.dashboardPendingReservations }
                            div(className = "flex flex-col gap-3") {
                                if (data.pendingReservations.isEmpty()) {
                                    p(className = "text-sm text-base-content/50 italic") { +currentStrings.dashboardAllPaid }
                                } else {
                                    data.pendingReservations.forEach { res ->
                                        AdminPendingReservationRow(
                                            name = res.contactName,
                                            eventName = res.eventName,
                                            price = formatAmount(res.totalPrice, currentStrings),
                                            vs = "${currentStrings.variableSymbol}: ${res.variableSymbol}",
                                            onMarkAsPaid = { model.markAsPaid(res.id, res.contactName) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Toast(
                message = model.toast?.message,
                type = model.toast?.type ?: ToastType.Success,
                onDismiss = { model.dismissToast() },
            )
        }
    }
}

@Composable
fun IComponent.AdminUpcomingEventRow(title: String, time: String, occupied: Int, capacity: Int, onClick: () -> Unit) {
    val currentStrings by strings
    val level = capacityLevel(occupied, capacity)
    val progressClass = when (level) {
        CapacityLevel.FULL -> "progress-error"
        CapacityLevel.NEARLY_FULL -> "progress-warning"
        CapacityLevel.OK -> "progress-success"
    }

    div(className = "flex flex-col gap-2 p-3 bg-base-200/50 rounded-lg hover:bg-base-200 transition-colors cursor-pointer") {
        onClick { onClick() }
        div(className = "flex justify-between items-start") {
            div {
                div(className = "font-bold text-sm") { +title }
                div(className = "text-xs text-base-content/60 mt-1 flex items-center gap-1") {
                    span(className = "icon-[heroicons--clock] size-3")
                    +time
                }
            }
            if (level == CapacityLevel.FULL) {
                div(className = "badge badge-error badge-sm font-bold") { +currentStrings.capacityFull }
            } else {
                div(className = "text-xs font-bold text-base-content/70") { +"$occupied / $capacity" }
            }
        }

        progress(className = "progress $progressClass w-full h-2") {
            attribute("value", occupied.toString())
            attribute("max", capacity.toString())
            attribute("aria-label", currentStrings.ariaCapacityProgress(occupied, capacity))
        }
    }
}

@Composable
fun IComponent.AdminPendingReservationRow(name: String, eventName: String, price: String, vs: String, onMarkAsPaid: () -> Unit) {
    val currentStrings by strings
    div(className = "flex justify-between items-center p-3 border border-base-200 rounded-lg hover:border-warning/50 transition-colors") {
        div(className = "flex flex-col") {
            span(className = "font-bold text-sm") { +name }
            span(className = "text-xs text-base-content/60 truncate max-w-[150px] sm:max-w-[200px]") { +eventName }
            span(className = "text-xs font-mono text-base-content/40 mt-1") { +vs }
        }
        div(className = "flex items-center gap-3") {
            span(className = "font-bold text-warning whitespace-nowrap") { +price }
            button(className = "btn btn-circle btn-ghost btn-sm text-success") {
                title(currentStrings.tooltipMarkPaid)
                attribute("aria-label", currentStrings.tooltipMarkPaid)
                onClick { onMarkAsPaid() }
                span(className = "icon-[heroicons--check] size-5")
            }
        }
    }
}
