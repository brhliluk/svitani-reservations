package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import dev.kilua.core.IComponent
import dev.kilua.html.*
import dev.kilua.rpc.getService

private sealed interface AdminEventsUiState {
    data object Loading : AdminEventsUiState
    data class Success(val data: List<AdminEventListItem>) : AdminEventsUiState
    data class Error(val message: String) : AdminEventsUiState
}

@Composable
fun IComponent.AdminEventsScreen() {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)

    val uiState by produceState<AdminEventsUiState>(initialValue = AdminEventsUiState.Loading) {
        adminService.getAllEvents()
            .onRight { value = AdminEventsUiState.Success(it) }
            .onLeft { value = AdminEventsUiState.Error(it.localizedMessage) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HLAVIČKA A TLAČÍTKA ---
        div(className = "flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4") {
            div {
                h1(className = "text-3xl font-bold text-base-content") { +"Události a Kurzy" }
                p(className = "text-base-content/60 mt-1") { +"Správa katalogu a otevírání nových termínů" }
            }

            // Magické tlačítko, které nás později hodí na formulář
            button(className = "btn btn-primary") {
                span(className = "icon-[heroicons--plus] size-5")
                +"Vytvořit novou"
                // onClick { router.navigate("/admin/events/new") } // Přidáme později
            }
        }

        // --- 2. TABULKA ---
        when (val state = uiState) {
            is AdminEventsUiState.Loading -> Loading()
            is AdminEventsUiState.Error -> {
                div(className = "alert alert-error") { +"Chyba načítání: ${state.message}" }
            }
            is AdminEventsUiState.Success -> {
                val data = state.data

                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body p-0") {
                        div(className = "overflow-x-auto") {
                            table(className = "table table-zebra w-full table-pin-rows") {
                                thead {
                                    tr {
                                        th { +"Název" }
                                        th { +"Typ" }
                                        th { +"Termín" }
                                        th { +"Obsazenost" }
                                        th { +"Cena" }
                                        th(className = "text-right") { +"Akce" }
                                    }
                                }
                                tbody {
                                    if (data.isEmpty()) {
                                        tr {
                                            td {
                                                attribute("colspan", "6")
                                                div(className = "text-center text-base-content/50 py-8") { +"Zatím tu nic není. Vytvořte první událost!" }
                                            }
                                        }
                                    } else {
                                        data.forEach { item ->
                                            // Celý řádek bude klikací a hodí nás na detail
                                            tr(className = "hover cursor-pointer") {
                                                onClick {
                                                    val typePath = if (item.isSeries) "series" else "instance"
                                                    router.navigate("/admin/events/$typePath/${item.id}")
                                                }

                                                td(className = "font-bold") { +item.title }
                                                td {
                                                    if (item.isSeries) {
                                                        div(className = "badge badge-secondary badge-outline gap-1") {
                                                            span(className = "icon-[heroicons--academic-cap] size-3")
                                                            +"Kurz"
                                                        }
                                                    } else {
                                                        div(className = "badge badge-primary badge-outline gap-1") {
                                                            span(className = "icon-[heroicons--calendar] size-3")
                                                            +"Jednorázovka"
                                                        }
                                                    }
                                                }
                                                td(className = "text-sm text-base-content/70") { +item.dateInfo }
                                                td {
                                                    val isFull = item.occupiedSpots >= item.capacity
                                                    div(className = "flex items-center gap-2") {
                                                        span(className = if (isFull) "text-error font-bold" else "") {
                                                            +"${item.occupiedSpots} / ${item.capacity}"
                                                        }
                                                        if (isFull) {
                                                            div(className = "badge badge-error badge-xs") { +"PLNO" }
                                                        }
                                                    }
                                                }
                                                td(className = "font-medium text-base-content/80") { +item.priceString }
                                                td(className = "text-right") {
                                                    // Ikonka pro jasný signál "klikni sem"
                                                    button(className = "btn btn-ghost btn-xs btn-circle") {
                                                        span(className = "icon-[heroicons--chevron-right] size-5 text-base-content/50")
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
            }
        }
    }
}