package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminReservationListItem
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.form
import dev.kilua.form.text.text
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch

// UI Stavy
private sealed interface AdminReservationsUiState {
    data object Loading : AdminReservationsUiState
    data class Success(val data: List<AdminReservationListItem>) : AdminReservationsUiState
    data class Error(val message: String) : AdminReservationsUiState
}

@Composable
fun IComponent.AdminReservationsScreen() {
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()

    var refreshTrigger by remember { mutableStateOf(0) }
    var toastData by remember { mutableStateOf<ToastData?>(null) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

    // Stavy pro vyhledávání
    var searchInput by remember { mutableStateOf("") }
    var activeSearchQuery by remember { mutableStateOf<String?>(null) }

    // Načítání dat (reaguje na refreshTrigger i na změnu activeSearchQuery)
    val uiState by produceState<AdminReservationsUiState>(
        initialValue = AdminReservationsUiState.Loading,
        key1 = refreshTrigger,
        key2 = activeSearchQuery
    ) {
        value = AdminReservationsUiState.Loading
        adminService.getAllReservations(activeSearchQuery)
            .onRight { value = AdminReservationsUiState.Success(it) }
            .onLeft { value = AdminReservationsUiState.Error(it.localizedMessage) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HLAVIČKA A VYHLEDÁVÁNÍ ---
        div(className = "flex flex-col md:flex-row justify-between items-start md:items-center gap-4") {
            div {
                h1(className = "text-3xl font-bold text-base-content") { +"Všechny rezervace" }
                p(className = "text-base-content/60 mt-1") { +"Přehled a správa všech přihlášek" }
            }

            // Vyhledávací lišta (DaisyUI Join komponenta)
            div(className = "join w-full md:w-auto") {
                div(className = "relative w-full md:w-80") {
                    span(className = "absolute inset-y-0 left-3 flex items-center pointer-events-none text-base-content/50") {
                        span(className = "icon-[heroicons--magnifying-glass] size-5")
                    }
                    text(value = searchInput, className = "input input-bordered join-item w-full pl-10") {
                        placeholder("Hledat jméno, e-mail nebo VS...")
                        onInput { searchInput = value ?: "" }
                        // Potvrzení Enterem
                        onKeyup { event ->
                            if (event.key == "Enter") activeSearchQuery = searchInput.takeIf { it.isNotBlank() }
                        }
                    }
                }
                button(className = "btn btn-primary join-item") {
                    onClick { activeSearchQuery = searchInput.takeIf { it.isNotBlank() } }
                    +"Hledat"
                }
                // Tlačítko pro vymazání filtru (zobrazí se jen když hledáme)
                if (!activeSearchQuery.isNullOrBlank()) {
                    button(className = "btn btn-ghost join-item tooltip") {
                        attribute("data-tip", "Zrušit vyhledávání")
                        onClick {
                            searchInput = ""
                            activeSearchQuery = null
                        }
                        span(className = "icon-[heroicons--x-mark] size-5")
                    }
                }
            }
        }

        // --- 2. TABULKA REZERVACÍ ---
        when (val state = uiState) {
            is AdminReservationsUiState.Loading -> Loading()
            is AdminReservationsUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminReservationsUiState.Success -> {
                val data = state.data

                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body p-0") {
                        div(className = "overflow-x-auto") {
                            table(className = "table table-zebra w-full") {
                                thead {
                                    tr {
                                        th { +"Účastník" }
                                        th { +"Událost / Kurz" }
                                        th { +"Místa" }
                                        th { +"Cena" }
                                        th { +"Stav" }
                                        th(className = "text-right") { +"Akce" }
                                    }
                                }
                                tbody {
                                    if (data.isEmpty()) {
                                        tr {
                                            td {
                                                attribute("colspan", "6")
                                                div(className = "text-center text-base-content/50 py-8") {
                                                    if (activeSearchQuery != null) +"Nebyly nalezeny žádné rezervace pro '$activeSearchQuery'."
                                                    else +"Zatím neexistují žádné rezervace."
                                                }
                                            }
                                        }
                                    } else {
                                        data.forEach { res ->
                                            val isPaid = res.status == Reservation.Status.CONFIRMED
                                            val isCash = res.paymentType == PaymentInfo.Type.ON_SITE

                                            tr(className = if (!isPaid && isCash) "bg-info/5" else "") {
                                                td {
                                                    div(className = "font-bold") { +res.contactName }
                                                    div(className = "text-xs text-base-content/50") { +res.contactEmail }
                                                }
                                                td {
                                                    div(className = "font-bold text-sm") { +res.eventTitle }
                                                    div(className = "text-xs text-base-content/50") { +res.eventDate }
                                                }
                                                td { +"${res.seatCount}" }
                                                td(className = if (!isPaid && isCash) "font-bold text-info" else "") {
                                                    div { +"${res.totalPrice} Kč" }
                                                    if (!res.variableSymbol.isNullOrBlank()) {
                                                        div(className = "text-xs font-mono text-base-content/40 mt-1") {
                                                            +"VS: ${res.variableSymbol}"
                                                        }
                                                    }
                                                }
                                                td {
                                                    div(className = "flex flex-col gap-1 items-start") {
                                                        if (isPaid) {
                                                            div(className = "badge badge-success gap-1") {
                                                                span(className = "icon-[heroicons--check] size-3")
                                                                +"Zaplaceno"
                                                            }
                                                        } else if (isCash) {
                                                            div(className = "badge badge-info badge-outline gap-1") {
                                                                span(className = "icon-[heroicons--banknotes] size-3")
                                                                +"Na místě"
                                                            }
                                                        } else {
                                                            div(className = "badge badge-warning gap-1") {
                                                                span(className = "icon-[heroicons--clock] size-3")
                                                                +"Čeká"
                                                            }
                                                        }
                                                        span(className = "text-xs text-base-content/60 font-medium") {
                                                            if (isCash) +"Hotově" else +"Převodem"
                                                        }
                                                    }
                                                }
                                                // Sloupec 6: Akce
                                                td(className = "text-right") {
                                                    div(className = "flex justify-end gap-1") {
                                                        if (!isPaid) {
                                                            button(className = "btn btn-xs tooltip tooltip-left ${if (isCash) "btn-outline btn-info" else "btn-ghost text-success"}") {
                                                                attribute("data-tip", if (isCash) "Přijmout hotovost" else "Označit jako zaplacené")
                                                                onClick {
                                                                    pendingAction = PendingAction(AdminActionType.CONFIRM_PAYMENT, res.id, res.contactName)
                                                                }
                                                                span(className = "icon-[heroicons--check-circle] size-5")
                                                                if (isCash) +"Vybrat"
                                                            }
                                                        }
                                                        button(className = "btn btn-ghost btn-xs text-error tooltip tooltip-left") {
                                                            attribute("data-tip", "Zrušit rezervaci")
                                                            onClick {
                                                                pendingAction = PendingAction(AdminActionType.CANCEL_RESERVATION, res.id, res.contactName)
                                                            }
                                                            span(className = "icon-[heroicons--trash] size-5")
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

    // --- 3. MODÁLNÍ OKNO A TOAST ---
    if (pendingAction != null) {
        val action = pendingAction!!
        div(className = "modal modal-open") {
            div(className = "modal-box") {
                h3(className = "font-bold text-lg") {
                    if (action.type == AdminActionType.CONFIRM_PAYMENT) +"Potvrdit platbu" else +"Zrušit rezervaci"
                }
                p(className = "py-4") {
                    if (action.type == AdminActionType.CONFIRM_PAYMENT) {
                        +"Opravdu chcete označit rezervaci pro účastníka "; strong { +action.participantName }; +" jako zaplacenou?"
                    } else {
                        +"Opravdu chcete zrušit rezervaci pro účastníka "; strong { +action.participantName }; +"? Tato akce je nevratná."
                    }
                }
                div(className = "modal-action") {
                    button(className = "btn") {
                        onClick { pendingAction = null }
                        +"Zpět"
                    }
                    button(className = "btn ${if (action.type == AdminActionType.CONFIRM_PAYMENT) "btn-success" else "btn-error"}") {
                        onClick {
                            scope.launch {
                                if (action.type == AdminActionType.CONFIRM_PAYMENT) {
                                    adminService.markReservationAsPaid(action.reservationId)
                                        .onRight {
                                            toastData = ToastData("Platba od ${action.participantName} potvrzena!", ToastType.Success)
                                            refreshTrigger++
                                        }
                                        .onLeft { error -> toastData = ToastData("Chyba: $error", ToastType.Error) }
                                } else {
                                    toastData = ToastData("Zatím nepřipojeno k backendu.", ToastType.Warning)
                                }
                                pendingAction = null
                            }
                        }
                        if (action.type == AdminActionType.CONFIRM_PAYMENT) +"Ano, potvrdit" else +"Ano, zrušit"
                    }
                }
            }
            form(className = "modal-backdrop") {
                button { onClick { pendingAction = null }; +"close" }
            }
        }
    }

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}