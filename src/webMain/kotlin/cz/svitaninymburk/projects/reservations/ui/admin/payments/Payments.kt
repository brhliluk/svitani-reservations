package cz.svitaninymburk.projects.reservations.ui.admin.payments

import cz.svitaninymburk.projects.reservations.ui.util.formatAmountNumber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.ui.admin.payments.usecase.PAYMENTS_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.pageCount
import cz.svitaninymburk.projects.reservations.ui.util.Pagination
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.html.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun IComponent.AdminPaymentsScreen() {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildAdminPaymentsModel(scope) }

    LaunchedEffect(Unit) { model.load() }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HEADER ---
        div {
            h1(className = "text-3xl font-bold text-base-content") { +currentStrings.allPayments }
            p(className = "text-base-content/60 mt-1") { +currentStrings.paymentsSubtitle }
        }

        // --- 2. TABLE ---
        when (val state = model.uiState) {
            is AdminPaymentsUiState.Loading -> Loading()
            is AdminPaymentsUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminPaymentsUiState.Success -> {
                val data = state.data
                val totalPages = pageCount(data.totalCount, PAYMENTS_PAGE_SIZE)

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
                                                td {
                                                    +event.processedAt
                                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                                        .humanReadable
                                                }
                                                // Contact name (plain text — no reservation detail route exists for admin)
                                                td {
                                                    +event.contactName
                                                }
                                                td {
                                                    +"${formatAmountNumber(event.amount)} ${event.currency}"
                                                }
                                                td {
                                                    when (event.type) {
                                                        PaymentType.BANK_TRANSFER -> {
                                                            div(className = "badge badge-info") {
                                                                +currentStrings.paymentTypeBankTransfer
                                                            }
                                                        }
                                                        PaymentType.ON_SITE -> {
                                                            div(className = "badge badge-success") {
                                                                +currentStrings.paymentTypeCash
                                                            }
                                                        }
                                                        PaymentType.FREE -> {
                                                            div(className = "badge badge-ghost") {
                                                                +currentStrings.paymentTypeFree
                                                            }
                                                        }
                                                    }
                                                }
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
                    Pagination(
                        page = model.page,
                        totalPages = totalPages,
                        onPageChange = { model.goToPage(it) },
                    )
                }
            }
        }
    }
}
