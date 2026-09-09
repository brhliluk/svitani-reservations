package cz.svitaninymburk.projects.reservations.ui.admin.reservations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.RESERVATIONS_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.ReservationPaymentMethod
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.reservationPaymentMethod
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.ReservationStatusBadge
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.ui.util.canBeMarkedAsPaid
import cz.svitaninymburk.projects.reservations.ui.util.pageCount
import cz.svitaninymburk.projects.reservations.ui.util.reservationStatusBadge
import cz.svitaninymburk.projects.reservations.ui.util.totalPriceLabel
import cz.svitaninymburk.projects.reservations.ui.util.Pagination
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.form.check.checkBox
import dev.kilua.form.text.text
import dev.kilua.html.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun IComponent.AdminReservationsScreen() {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildAdminReservationsModel(scope) }

    LaunchedEffect(Unit) { model.load() }

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
                    text(value = model.searchInput, className = "input input-bordered w-full pl-10") {
                        placeholder(currentStrings.searchPlaceholder)
                        onInput { model.searchInput = value ?: "" }
                        onKeyup { event -> if (event.key == "Enter") model.submitSearch() }
                    }
                }
                button(className = "btn btn-primary") {
                    onClick { model.submitSearch() }
                    +currentStrings.search
                }
                if (!model.activeSearchQuery.isNullOrBlank()) {
                    button(className = "btn btn-ghost tooltip") {
                        attribute("data-tip", currentStrings.clearSearch)
                        onClick { model.clearSearch() }
                        span(className = "icon-[heroicons--x-mark] size-5")
                    }
                }
                label(className = "flex items-center gap-2 cursor-pointer select-none") {
                    span(className = "text-sm text-base-content/70") { +currentStrings.showCancelledReservations }
                    checkBox(value = model.includeCancelled, className = "toggle toggle-error toggle-sm") {
                        onChange { model.setIncludeCancelled(value) }
                    }
                }
            }
        }

        // --- 2. TABULKA REZERVACÍ ---
        when (val state = model.uiState) {
            is AdminReservationsUiState.Loading -> Loading()
            is AdminReservationsUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminReservationsUiState.Success -> {
                val data = state.data
                val totalPages = pageCount(data.totalCount, RESERVATIONS_PAGE_SIZE)

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
                                    if (data.items.isEmpty()) {
                                        tr {
                                            td {
                                                attribute("colspan", "7")
                                                div(className = "text-center text-base-content/50 py-8") {
                                                    val query = model.activeSearchQuery
                                                    if (query != null) +currentStrings.noReservationsForSearch(query)
                                                    else +currentStrings.noReservations
                                                }
                                            }
                                        }
                                    } else {
                                        data.items.forEach { res ->
                                            val badge = reservationStatusBadge(res.status, res.paymentType, res.totalPrice)
                                            val isPaid = badge == ReservationStatusBadge.PAID || badge == ReservationStatusBadge.FREE
                                            val isCash = badge == ReservationStatusBadge.ON_SITE
                                            val isCancelled = badge == ReservationStatusBadge.CANCELLED
                                            val isExpanded = model.expandedId == res.id

                                            val trClass = when {
                                                isCancelled -> "opacity-40"
                                                !isPaid && isCash -> "bg-info/5"
                                                else -> ""
                                            }
                                            tr(className = trClass) {
                                                td {
                                                    button(className = "btn btn-ghost btn-xs tooltip tooltip-right") {
                                                        attribute("data-tip", if (isExpanded) currentStrings.hideDetails else currentStrings.showDetails)
                                                        onClick { model.toggleExpanded(res.id) }
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
                                                    div { +totalPriceLabel(res.totalPrice, currentStrings) }
                                                    if (!res.variableSymbol.isNullOrBlank()) {
                                                        div(className = "text-xs font-mono text-base-content/40 mt-1") {
                                                            +"${currentStrings.variableSymbol}: ${res.variableSymbol}"
                                                        }
                                                    }
                                                }
                                                td {
                                                    div(className = "flex flex-col gap-1 items-start") {
                                                        when (badge) {
                                                            ReservationStatusBadge.CANCELLED -> div(className = "badge badge-error gap-1") {
                                                                span(className = "icon-[heroicons--x-mark] size-3")
                                                                +currentStrings.cancelled
                                                            }
                                                            ReservationStatusBadge.WAITLISTED -> div(className = "badge badge-secondary badge-outline gap-1 whitespace-nowrap") {
                                                                span(className = "icon-[heroicons--queue-list] size-3")
                                                                +currentStrings.waitlistedStatus
                                                            }
                                                            ReservationStatusBadge.FREE -> div(className = "badge badge-success badge-outline gap-1") {
                                                                span(className = "icon-[heroicons--gift] size-3")
                                                                +currentStrings.free
                                                            }
                                                            ReservationStatusBadge.PAID -> div(className = "badge badge-success gap-1") {
                                                                span(className = "icon-[heroicons--check] size-3")
                                                                +currentStrings.paid
                                                            }
                                                            ReservationStatusBadge.ON_SITE -> div(className = "badge badge-info badge-outline gap-1 whitespace-nowrap") {
                                                                span(className = "icon-[heroicons--banknotes] size-3")
                                                                +currentStrings.statusOnSiteBadge
                                                            }
                                                            ReservationStatusBadge.WAITING -> div(className = "badge badge-warning gap-1") {
                                                                span(className = "icon-[heroicons--clock] size-3")
                                                                +currentStrings.statusWaiting
                                                            }
                                                        }
                                                        val method = reservationPaymentMethod(res.totalPrice, res.walletDeductedAmount, isCash)
                                                        if (method != null) {
                                                            span(className = "text-xs text-base-content/60 font-medium") {
                                                                when (method) {
                                                                    ReservationPaymentMethod.WALLET -> +currentStrings.paymentMethodWallet
                                                                    ReservationPaymentMethod.CASH_AND_WALLET -> +currentStrings.paymentMethodCashAndWallet
                                                                    ReservationPaymentMethod.TRANSFER_AND_WALLET -> +currentStrings.paymentMethodBankTransferAndWallet
                                                                    ReservationPaymentMethod.CASH -> +currentStrings.paymentMethodCash
                                                                    ReservationPaymentMethod.TRANSFER -> +currentStrings.bankTransfer
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                // Sloupec 6: Akce
                                                td(className = "text-right") {
                                                    if (!isCancelled) {
                                                        div(className = "flex justify-end items-center gap-1") {
                                                            if (canBeMarkedAsPaid(badge)) {
                                                                button(className = "btn btn-xs inline-flex items-center gap-1 tooltip tooltip-left ${if (isCash) "btn-outline btn-info" else "btn-ghost text-success"}") {
                                                                    attribute("data-tip", if (isCash) currentStrings.tooltipAcceptCash else currentStrings.tooltipMarkPaid)
                                                                    onClick { model.confirmPayment(res) }
                                                                    span(className = "icon-[heroicons--check-circle] size-5 flex-none")
                                                                    if (isCash) +currentStrings.buttonCollect
                                                                }
                                                            }
                                                            button(className = "btn btn-ghost btn-xs text-error tooltip tooltip-left") {
                                                                attribute("data-tip", currentStrings.tooltipCancelReservation)
                                                                onClick { model.cancelReservation(res) }
                                                                span(className = "icon-[heroicons--trash] size-5")
                                                            }
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

                // --- 3. PAGINATION ---
                if (data.totalCount > RESERVATIONS_PAGE_SIZE) {
                    Pagination(
                        page = model.page,
                        totalPages = totalPages,
                        onPageChange = { model.goToPage(it) },
                    )
                }
            }
        }
    }

    // --- 4. MODÁLNÍ OKNO A TOAST ---
    model.pendingAction?.let { action ->
        ReservationActionModal(
            action = action,
            isLoading = model.isModalLoading,
            onConfirm = { model.confirmPendingAction(action) },
            onDismiss = { model.dismissPendingAction() },
        )
    }

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Success,
        onDismiss = { model.dismissToast() },
    )
}
