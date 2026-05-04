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
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.reservation.CustomFieldsDisplay
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.form.form
import dev.kilua.form.text.text
import dev.kilua.html.*
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

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
    val currentStrings by strings

    var refreshTrigger by remember { mutableStateOf(0) }
    var toastData by remember { mutableStateOf<ToastData?>(null) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var expandedId by remember { mutableStateOf<Uuid?>(null) }

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
            .onLeft { value = AdminReservationsUiState.Error(it.localizedMessage(currentStrings)) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HLAVIČKA A VYHLEDÁVÁNÍ ---
        div(className = "flex flex-col md:flex-row justify-between items-start md:items-center gap-4") {
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.allReservations }
                p(className = "text-base-content/60 mt-1") { +currentStrings.reservationsSubtitle }
            }

            // Vyhledávací lišta
            div(className = "flex items-center w-full md:w-auto gap-2") {
                div(className = "relative w-full md:w-80") {
                    span(className = "absolute inset-y-0 left-3 flex items-center pointer-events-none text-base-content/50") {
                        span(className = "icon-[heroicons--magnifying-glass] size-5")
                    }
                    text(value = searchInput, className = "input input-bordered w-full pl-10") {
                        placeholder(currentStrings.searchPlaceholder)
                        onInput { searchInput = value ?: "" }
                        onKeyup { event ->
                            if (event.key == "Enter") activeSearchQuery = searchInput.takeIf { it.isNotBlank() }
                        }
                    }
                }
                button(className = "btn btn-primary") {
                    onClick { activeSearchQuery = searchInput.takeIf { it.isNotBlank() } }
                    +currentStrings.search
                }
                if (!activeSearchQuery.isNullOrBlank()) {
                    button(className = "btn btn-ghost tooltip") {
                        attribute("data-tip", currentStrings.clearSearch)
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
                                        th(className = "w-10") { }
                                        th { +currentStrings.tableHeaderParticipant }
                                        th { +currentStrings.tableHeaderEvent }
                                        th { +currentStrings.tableHeaderSeats }
                                        th { +currentStrings.priceLabel }
                                        th { +currentStrings.status }
                                        th(className = "text-right") { +currentStrings.tableHeaderActions }
                                    }
                                }
                                tbody {
                                    if (data.isEmpty()) {
                                        tr {
                                            td {
                                                attribute("colspan", "7")
                                                div(className = "text-center text-base-content/50 py-8") {
                                                    if (activeSearchQuery != null) +currentStrings.noReservationsForSearch(activeSearchQuery!!)
                                                    else +currentStrings.noReservations
                                                }
                                            }
                                        }
                                    } else {
                                        data.forEach { res ->
                                            val isPaid = res.status == Reservation.Status.CONFIRMED
                                            val isCash = res.paymentType == PaymentInfo.Type.ON_SITE
                                            val isExpanded = expandedId == res.id

                                            tr(className = if (!isPaid && isCash) "bg-info/5" else "") {
                                                td {
                                                    button(className = "btn btn-ghost btn-xs tooltip tooltip-right") {
                                                        attribute("data-tip", if (isExpanded) currentStrings.hideDetails else currentStrings.showDetails)
                                                        onClick { expandedId = if (isExpanded) null else res.id }
                                                        span(className = "size-5 " + if (isExpanded) "icon-[heroicons--chevron-down]" else "icon-[heroicons--chevron-right]")
                                                    }
                                                }
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
                                                    div { +"${res.totalPrice} ${currentStrings.currency}" }
                                                    if (!res.variableSymbol.isNullOrBlank()) {
                                                        div(className = "text-xs font-mono text-base-content/40 mt-1") {
                                                            +"${currentStrings.variableSymbol}: ${res.variableSymbol}"
                                                        }
                                                    }
                                                }
                                                td {
                                                    div(className = "flex flex-col gap-1 items-start") {
                                                        if (isPaid) {
                                                            div(className = "badge badge-success gap-1") {
                                                                span(className = "icon-[heroicons--check] size-3")
                                                                +currentStrings.paid
                                                            }
                                                        } else if (isCash) {
                                                            div(className = "badge badge-info badge-outline gap-1 whitespace-nowrap") {
                                                                span(className = "icon-[heroicons--banknotes] size-3")
                                                                +currentStrings.statusOnSiteBadge
                                                            }
                                                        } else {
                                                            div(className = "badge badge-warning gap-1") {
                                                                span(className = "icon-[heroicons--clock] size-3")
                                                                +currentStrings.statusWaiting
                                                            }
                                                        }
                                                        span(className = "text-xs text-base-content/60 font-medium") {
                                                            if (isCash) +currentStrings.paymentMethodCash else +currentStrings.bankTransfer
                                                        }
                                                    }
                                                }
                                                // Sloupec 6: Akce
                                                td(className = "text-right") {
                                                    div(className = "flex justify-end gap-1") {
                                                        if (!isPaid) {
                                                            button(className = "btn btn-xs tooltip tooltip-left ${if (isCash) "btn-outline btn-info" else "btn-ghost text-success"}") {
                                                                attribute("data-tip", if (isCash) currentStrings.tooltipAcceptCash else currentStrings.tooltipMarkPaid)
                                                                onClick {
                                                                    pendingAction = PendingAction(AdminActionType.CONFIRM_PAYMENT, res.id, res.contactName)
                                                                }
                                                                span(className = "icon-[heroicons--check-circle] size-5")
                                                                if (isCash) +currentStrings.buttonCollect
                                                            }
                                                        }
                                                        button(className = "btn btn-ghost btn-xs text-error tooltip tooltip-left") {
                                                            attribute("data-tip", currentStrings.tooltipCancelReservation)
                                                            onClick {
                                                                pendingAction = PendingAction(AdminActionType.CANCEL_RESERVATION, res.id, res.contactName)
                                                            }
                                                            span(className = "icon-[heroicons--trash] size-5")
                                                        }
                                                    }
                                                }
                                            }

                                            if (isExpanded) {
                                                tr(className = "bg-base-200/40") {
                                                    td {
                                                        attribute("colspan", "7")
                                                        div(className = "p-4") {
                                                            ReservationExpandedDetails(
                                                                phone = res.contactPhone,
                                                                createdAtText = res.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).humanReadable,
                                                                customFields = res.customFields,
                                                                customValues = res.customValues,
                                                            )
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
                    if (action.type == AdminActionType.CONFIRM_PAYMENT) +currentStrings.modalConfirmPaymentTitle else +currentStrings.modalCancelReservationTitle
                }
                p(className = "py-4") {
                    if (action.type == AdminActionType.CONFIRM_PAYMENT) {
                        +currentStrings.modalConfirmPaymentMsgPre; strong { +action.participantName }; +currentStrings.modalConfirmPaymentMsgPost
                    } else {
                        +currentStrings.modalCancelMsgPre; strong { +action.participantName }; +currentStrings.modalCancelMsgPost
                    }
                }
                div(className = "modal-action") {
                    button(className = "btn") {
                        onClick { pendingAction = null }
                        +currentStrings.modalBack
                    }
                    button(className = "btn ${if (action.type == AdminActionType.CONFIRM_PAYMENT) "btn-success" else "btn-error"}") {
                        onClick {
                            scope.launch {
                                if (action.type == AdminActionType.CONFIRM_PAYMENT) {
                                    adminService.markReservationAsPaid(action.reservationId)
                                        .onRight {
                                            toastData = ToastData(currentStrings.toastPaymentConfirmed(action.participantName), ToastType.Success)
                                            refreshTrigger++
                                        }
                                        .onLeft { error -> toastData = ToastData(currentStrings.errorToast(error.toString()), ToastType.Error) }
                                } else {
                                    toastData = ToastData("Zatím nepřipojeno k backendu.", ToastType.Warning)
                                }
                                pendingAction = null
                            }
                        }
                        if (action.type == AdminActionType.CONFIRM_PAYMENT) +currentStrings.modalConfirmAction else +currentStrings.modalConfirmCancelAction
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

@Composable
internal fun IComponent.ReservationExpandedDetails(
    phone: String?,
    createdAtText: String,
    customFields: List<CustomFieldDefinition>,
    customValues: Map<String, CustomFieldValue>,
) {
    val currentStrings by strings

    div(className = "grid grid-cols-1 md:grid-cols-2 gap-6") {
        div(className = "flex flex-col gap-2") {
            if (!phone.isNullOrBlank()) {
                div(className = "flex justify-between items-baseline gap-4") {
                    span(className = "text-xs uppercase font-bold tracking-wider text-base-content/60") { +currentStrings.phoneLabel }
                    span(className = "font-medium text-base-content text-sm") { +phone }
                }
            }
            div(className = "flex justify-between items-baseline gap-4") {
                span(className = "text-xs uppercase font-bold tracking-wider text-base-content/60") { +currentStrings.createdAt }
                span(className = "font-medium text-base-content text-sm") { +createdAtText }
            }
        }

        if (customFields.isNotEmpty()) {
            div {
                div(className = "text-xs uppercase font-bold tracking-wider text-base-content/60 mb-2") {
                    +currentStrings.customFieldsHeading
                }
                CustomFieldsDisplay(customFields, customValues)
            }
        }
    }
}