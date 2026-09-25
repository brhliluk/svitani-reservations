package cz.svitaninymburk.projects.reservations.ui.admin.wallet

import cz.svitaninymburk.projects.reservations.ui.util.signedAmount
import cz.svitaninymburk.projects.reservations.ui.util.formatAmount
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.wallet.usecase.WALLETS_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.CopyToClipboardButton
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.ui.util.pageCount
import cz.svitaninymburk.projects.reservations.ui.util.Pagination
import cz.svitaninymburk.projects.reservations.util.humanReadable
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import dev.kilua.core.IComponent
import dev.kilua.form.number.numeric
import app.softwork.routingcompose.Router
import dev.kilua.form.text.text
import dev.kilua.html.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun IComponent.AdminWalletsScreen(preselectCode: String? = null) {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val router = Router.current
    val model = remember(preselectCode) { buildAdminWalletsModel(scope, router, preselectCode) }

    LaunchedEffect(preselectCode) { model.load() }

    val selected = model.selectedWallet
    if (selected != null) {
        AdminWalletDetailPanel(wallet = selected, onBack = { model.closeWallet() })
        return
    }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HEADER ---
        div {
            h1(className = "text-3xl font-bold text-base-content") { +currentStrings.adminWallets }
        }

        // --- 2. TABLE ---
        when (val state = model.uiState) {
            is AdminWalletsUiState.Loading -> Loading()
            is AdminWalletsUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminWalletsUiState.Success -> {
                val data = state.data
                val totalPages = pageCount(data.totalCount, WALLETS_PAGE_SIZE)

                if (data.totalCount == 0L) {
                    div(className = "text-center text-base-content/50 py-12") {
                        span(className = "icon-[heroicons--wallet] size-12 opacity-30")
                    }
                } else {
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body p-0") {
                            div(className = "overflow-x-auto") {
                                table(className = "table table-zebra w-full") {
                                    thead {
                                        tr {
                                            th { +currentStrings.walletCode }
                                            th { +currentStrings.emailLabel }
                                            th { +currentStrings.walletBalance }
                                            th { +currentStrings.createdAt }
                                            th(className = "text-right") { +currentStrings.tableHeaderActions }
                                        }
                                    }
                                    tbody {
                                        data.items.forEach { wallet ->
                                            tr {
                                                td {
                                                    div(className = "font-mono font-bold text-sm") { +wallet.code }
                                                }
                                                td {
                                                    div(className = "text-sm") { +wallet.ownerEmail }
                                                }
                                                td {
                                                    val balanceClass = if (wallet.balance > 0) "text-success font-bold" else "text-base-content/60"
                                                    div(className = balanceClass) {
                                                        +formatAmount(wallet.balance, currentStrings)
                                                    }
                                                }
                                                td {
                                                    div(className = "text-xs text-base-content/60") {
                                                        +wallet.createdAt
                                                            .toLocalDateTime(TimeZone.currentSystemDefault())
                                                            .humanReadable
                                                    }
                                                }
                                                td(className = "text-right") {
                                                    button(className = "btn btn-ghost btn-xs tooltip tooltip-left") {
                                                        attribute("data-tip", currentStrings.tooltipViewWallet)
                                                        onClick { model.openWallet(wallet) }
                                                        span(className = "icon-[heroicons--eye] size-4")
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

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Success,
        onDismiss = { model.dismissToast() },
    )
}

@Composable
private fun IComponent.AdminWalletDetailPanel(wallet: Wallet, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember(wallet.id) { buildAdminWalletDetailModel(scope, wallet) }

    LaunchedEffect(wallet.id) { model.load() }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto") {

        // --- BACK + HEADER ---
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                onClick { onBack() }
                span(className = "icon-[heroicons--arrow-left] size-5")
            }
            div(className = "flex-1 min-w-0") {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.adminWalletDetail }
                div(className = "flex items-center gap-3 mt-1 flex-wrap") {
                    span(className = "text-base-content/50") { +model.wallet.ownerEmail }
                    span(className = "font-bold text-success") {
                        +formatAmount(model.wallet.balance, currentStrings)
                    }
                    model.resetDateLabel?.let { date ->
                        span(className = "text-xs text-base-content/50 flex items-center gap-1") {
                            span(className = "icon-[heroicons--calendar] size-3")
                            +"${currentStrings.walletExpiresOn}: $date"
                        }
                    }
                }
            }
        }

        CopyToClipboardButton(currentStrings.walletCode, model.wallet.code)

        // --- ADJUSTMENT FORM ---
        div(className = "card bg-base-100 shadow-sm") {
            div(className = "card-body") {
                h2(className = "card-title text-lg mb-4") { +currentStrings.adminWalletAdjust }
                div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                    div(className = "form-control w-full") {
                        label(className = "label") {
                            span(className = "label-text font-medium") { +currentStrings.walletBalance }
                        }
                        div(className = "relative flex items-center") {
                            numeric(value = model.adjustAmount, min = 0, className = "input input-bordered w-full pr-12") {
                                onInput { model.adjustAmount = value }
                                onChange { model.adjustAmount = value }
                            }
                            span(className = "absolute right-4 text-base-content/50 font-medium") {
                                +currentStrings.currency
                            }
                        }
                    }
                    div(className = "form-control w-full") {
                        label(className = "label") {
                            span(className = "label-text font-medium") { +currentStrings.adminWalletAdjustNote }
                        }
                        text(value = model.adjustNote, className = "input input-bordered w-full") {
                            onInput { model.adjustNote = value ?: "" }
                        }
                    }
                }
                div(className = "flex gap-2 mt-4") {
                    button(className = "btn btn-success gap-2") {
                        disabled(model.isSubmitting || !model.canAdjust)
                        onClick { model.adjust(isCredit = true) }
                        if (model.isSubmitting) span(className = "loading loading-spinner loading-sm")
                        span(className = "icon-[heroicons--plus] size-4")
                        +currentStrings.adminWalletCreditButton
                    }
                    button(className = "btn btn-error btn-outline gap-2") {
                        disabled(model.isSubmitting || !model.canAdjust)
                        onClick { model.adjust(isCredit = false) }
                        if (model.isSubmitting) span(className = "loading loading-spinner loading-sm")
                        span(className = "icon-[heroicons--minus] size-4")
                        +currentStrings.adminWalletDebitButton
                    }
                }
            }
        }

        // --- TRANSACTION HISTORY ---
        when (val state = model.uiState) {
            is AdminWalletDetailUiState.Loading -> Loading()
            is AdminWalletDetailUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminWalletDetailUiState.Success -> {
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body p-0") {
                        div(className = "px-6 pt-5 pb-3") {
                            h2(className = "card-title text-lg") { +currentStrings.adminWalletTransactions }
                        }
                        div(className = "overflow-x-auto") {
                            table(className = "table table-zebra w-full") {
                                thead {
                                    tr {
                                        th { +currentStrings.tableHeaderDate }
                                        th { +currentStrings.tableHeaderReason }
                                        th { +currentStrings.tableHeaderAmount }
                                        th { +currentStrings.tableHeaderNote }
                                    }
                                }
                                tbody {
                                    if (state.transactions.isEmpty()) {
                                        tr {
                                            td {
                                                attribute("colspan", "4")
                                                div(className = "text-center text-base-content/50 py-8") {
                                                    +currentStrings.adminWalletNoTransactions
                                                }
                                            }
                                        }
                                    } else {
                                        state.transactions.forEach { tx ->
                                            tr {
                                                td {
                                                    div(className = "text-xs text-base-content/70") {
                                                        +tx.createdAt
                                                            .toLocalDateTime(TimeZone.currentSystemDefault())
                                                            .humanReadable
                                                    }
                                                }
                                                td {
                                                    div(className = "text-xs font-mono") { +tx.reason.name }
                                                }
                                                td {
                                                    val amtClass = if (tx.amount >= 0) "text-success font-bold" else "text-error font-bold"
                                                    div(className = amtClass) {
                                                        +signedAmount(tx.amount, currentStrings)
                                                    }
                                                }
                                                td {
                                                    div(className = "text-xs text-base-content/60") {
                                                        +(tx.note ?: "")
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

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Success,
        onDismiss = { model.dismissToast() },
    )
}
