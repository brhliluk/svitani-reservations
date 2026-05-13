package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.PaymentEventsPage
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil

private const val PAGE_SIZE = 20

private sealed interface AdminPaymentsUiState {
    data object Loading : AdminPaymentsUiState
    data class Success(val data: PaymentEventsPage) : AdminPaymentsUiState
    data class Error(val message: String) : AdminPaymentsUiState
}

@Composable
fun IComponent.AdminPaymentsScreen() {
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val currentStrings by strings

    var page by remember { mutableStateOf(0) }

    val uiState by produceState<AdminPaymentsUiState>(
        initialValue = AdminPaymentsUiState.Loading,
        key1 = page
    ) {
        value = AdminPaymentsUiState.Loading
        adminService.getPaymentEvents(page, PAGE_SIZE)
            .onRight { value = AdminPaymentsUiState.Success(it) }
            .onLeft { value = AdminPaymentsUiState.Error(it.localizedMessage(currentStrings)) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HEADER ---
        div {
            h1(className = "text-3xl font-bold text-base-content") { +currentStrings.allPayments }
            p(className = "text-base-content/60 mt-1") { +currentStrings.paymentsSubtitle }
        }

        // --- 2. TABLE ---
        when (val state = uiState) {
            is AdminPaymentsUiState.Loading -> Loading()
            is AdminPaymentsUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminPaymentsUiState.Success -> {
                val data = state.data
                val totalPages = maxOf(1, ceil(data.totalCount.toDouble() / PAGE_SIZE).toInt())

                if (data.totalCount == 0L) {
                    div(className = "text-center text-base-content/50 py-12") {
                        +currentStrings.noPayments
                    }
                } else {
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body p-0") {
                            div(className = "overflow-x-auto") {
                                table(className = "table table-zebra w-full") {
                                    thead {
                                        tr {
                                            th { +currentStrings.tableHeaderProcessedAt }
                                            th { +currentStrings.tableHeaderContactName }
                                            th { +currentStrings.tableHeaderAmount }
                                            th { +currentStrings.tableHeaderPaymentType }
                                            th { +currentStrings.tableHeaderPaymentSource }
                                        }
                                    }
                                    tbody {
                                        data.items.forEach { event ->
                                            tr {
                                                // Processed at
                                                td {
                                                    +event.processedAt
                                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                                        .humanReadable
                                                }
                                                // Contact name (plain text — no reservation detail route exists for admin)
                                                td {
                                                    +event.contactName
                                                }
                                                // Amount
                                                td {
                                                    +"${event.amount.toInt()} ${event.currency}"
                                                }
                                                // Payment type badge
                                                td {
                                                    when (event.type) {
                                                        PaymentInfo.Type.BANK_TRANSFER -> {
                                                            div(className = "badge badge-info") {
                                                                +currentStrings.paymentTypeBankTransfer
                                                            }
                                                        }
                                                        PaymentInfo.Type.ON_SITE -> {
                                                            div(className = "badge badge-success") {
                                                                +currentStrings.paymentTypeCash
                                                            }
                                                        }
                                                        PaymentInfo.Type.FREE -> {
                                                            div(className = "badge badge-ghost") {
                                                                +currentStrings.paymentTypeFree
                                                            }
                                                        }
                                                    }
                                                }
                                                // Payment source
                                                td {
                                                    when (event.source) {
                                                        PaymentEvent.Source.AUTO_FIO -> +currentStrings.paymentSourceAutoFio
                                                        PaymentEvent.Source.MANUAL_ADMIN -> +currentStrings.paymentSourceManualAdmin
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
                    div(className = "flex items-center justify-center gap-4 mt-4") {
                        button(className = "btn btn-outline btn-sm") {
                            disabled(page == 0)
                            onClick { if (page > 0) page-- }
                            +currentStrings.paginationPrevious
                        }
                        span(className = "text-sm text-base-content/70") {
                            +currentStrings.paginationPageOf(page + 1, totalPages)
                        }
                        button(className = "btn btn-outline btn-sm") {
                            disabled(page >= totalPages - 1)
                            onClick { if (page < totalPages - 1) page++ }
                            +currentStrings.paginationNext
                        }
                    }
                }
            }
        }
    }
}
