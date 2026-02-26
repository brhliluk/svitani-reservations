package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.reservation.AdminDashboardData
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import dev.kilua.core.IComponent
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.datetime.number

private sealed interface AdminDashboardUiState {
    data object Loading : AdminDashboardUiState
    data class Success(val data: AdminDashboardData) : AdminDashboardUiState
    data class Error(val message: String) : AdminDashboardUiState
}

@Composable
fun IComponent.AdminDashboardScreen() {
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)

    // Stažení dat z backendu
    val uiState by produceState<AdminDashboardUiState>(initialValue = AdminDashboardUiState.Loading) {
        adminService.getDashboardSummary()
            .onRight { value = AdminDashboardUiState.Success(it) }
            .onLeft { value = AdminDashboardUiState.Error(it.toString()) }
    }

    when (val state = uiState) {
        is AdminDashboardUiState.Loading -> Loading()
        is AdminDashboardUiState.Error -> {
            div(className = "alert alert-error") { +"Chyba načítání dat: ${state.message}" }
        }
        is AdminDashboardUiState.Success -> {
            val data = state.data

            div(className = "flex flex-col gap-8 animate-fade-in") {
                // Hlavička
                div {
                    h1(className = "text-3xl font-bold text-base-content") { +"Přehled" }
                    p(className = "text-base-content/60 mt-1") { +"Vítejte zpět! Takhle to aktuálně vypadá s vašimi rezervacemi." }
                }

                // --- 1. KPI STATISTIKY ---
                div(className = "stats stats-vertical lg:stats-horizontal shadow-sm w-full bg-base-100") {
                    div(className = "stat") {
                        div(className = "stat-figure text-primary") { span(className = "icon-[heroicons--users] size-8") }
                        div(className = "stat-title") { +"Dnešní účastníci" }
                        div(className = "stat-value text-primary") { +"${data.todayParticipantsCount}" }
                    }
                    div(className = "stat") {
                        div(className = "stat-figure text-warning") { span(className = "icon-[heroicons--banknotes] size-8") }
                        div(className = "stat-title") { +"Čeká na platbu" }
                        div(className = "stat-value text-warning") { +"${data.pendingPaymentsTotal} Kč" }
                        div(className = "stat-desc") { +"Celkem ${data.pendingPaymentsCount} rezervací" }
                    }
                    div(className = "stat") {
                        div(className = "stat-figure text-info") { span(className = "icon-[heroicons--ticket] size-8") }
                        div(className = "stat-title") { +"Volná místa" }
                        div(className = "stat-value text-info") { +"${data.freeSpotsThisWeek}" }
                        div(className = "stat-desc") { +"Na akcích v tomto týdnu" }
                    }
                }

                // --- 2. HLAVNÍ OBSAH ---
                div(className = "grid grid-cols-1 lg:grid-cols-2 gap-8") {
                    // LEVÝ SLOUPEC: Události
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body p-6") {
                            h2(className = "card-title text-lg mb-4") { +"Nejbližší události" }
                            div(className = "flex flex-col gap-4") {
                                if (data.upcomingEvents.isEmpty()) {
                                    p(className = "text-sm text-base-content/50 italic") { +"Žádné nadcházející události." }
                                } else {
                                    data.upcomingEvents.forEach { event ->
                                        // Formátování data: "DD.MM.YYYY HH:mm"
                                        val timeString = "${event.startDateTime.date.day}.${event.startDateTime.date.month.number}. ${event.startDateTime.hour}:${event.startDateTime.minute.toString().padStart(2, '0')}"
                                        AdminUpcomingEventRow(event.title, timeString, event.occupiedSpots, event.capacity)
                                    }
                                }
                            }
                        }
                    }

                    // PRAVÝ SLOUPEC: Rezervace
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body p-6") {
                            h2(className = "card-title text-lg mb-4") { +"Poslední neuhrazené rezervace" }
                            div(className = "flex flex-col gap-3") {
                                if (data.pendingReservations.isEmpty()) {
                                    p(className = "text-sm text-base-content/50 italic") { +"Všechny rezervace jsou uhrazené!" }
                                } else {
                                    data.pendingReservations.forEach { res ->
                                        AdminPendingReservationRow(res.contactName, res.eventName, "${res.totalPrice} Kč", "VS: ${res.variableSymbol}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IComponent.AdminUpcomingEventRow(title: String, time: String, occupied: Int, capacity: Int) {
    val isFull = occupied >= capacity
    val progressClass = if (isFull) "progress-error" else if (occupied.toDouble() / capacity > 0.8) "progress-warning" else "progress-success"

    div(className = "flex flex-col gap-2 p-3 bg-base-200/50 rounded-lg hover:bg-base-200 transition-colors cursor-pointer") {
        div(className = "flex justify-between items-start") {
            div {
                div(className = "font-bold text-sm") { +title }
                div(className = "text-xs text-base-content/60 mt-1 flex items-center gap-1") {
                    span(className = "icon-[heroicons--clock] size-3")
                    +time
                }
            }
            if (isFull) {
                div(className = "badge badge-error badge-sm font-bold") { +"PLNO" }
            } else {
                div(className = "text-xs font-bold text-base-content/70") { +"$occupied / $capacity" }
            }
        }

        // Náš opravený Kilua progress bar pomocí attribute()
        progress(className = "progress $progressClass w-full h-2") {
            attribute("value", occupied.toString())
            attribute("max", capacity.toString())
        }
    }
}

@Composable
fun IComponent.AdminPendingReservationRow(name: String, eventName: String, price: String, vs: String) {
    div(className = "flex justify-between items-center p-3 border border-base-200 rounded-lg hover:border-warning/50 transition-colors") {
        div(className = "flex flex-col") {
            span(className = "font-bold text-sm") { +name }
            span(className = "text-xs text-base-content/60 truncate max-w-[150px] sm:max-w-[200px]") { +eventName }
            span(className = "text-xs font-mono text-base-content/40 mt-1") { +vs }
        }
        div(className = "flex items-center gap-3") {
            span(className = "font-bold text-warning whitespace-nowrap") { +price }
            // Tlačítko pro schválení platby (zatím vizuální)
            button(className = "btn btn-circle btn-ghost btn-sm text-success") {
                attribute("title", "Označit jako zaplacené")
                span(className = "icon-[heroicons--check] size-5")
            }
        }
    }
}