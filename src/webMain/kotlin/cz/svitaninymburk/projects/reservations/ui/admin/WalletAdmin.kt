package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.AppSettingsServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.util.humanReadable
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.CopyToClipboardButton
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletTransaction
import cz.svitaninymburk.projects.reservations.wallet.WalletsPage
import dev.kilua.core.IComponent
import dev.kilua.form.number.numeric
import dev.kilua.form.text.text
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil

private const val PAGE_SIZE = 20

private sealed interface AdminWalletsUiState {
    data object Loading : AdminWalletsUiState
    data class Success(val data: WalletsPage) : AdminWalletsUiState
    data class Error(val message: String) : AdminWalletsUiState
}

private sealed interface AdminWalletDetailUiState {
    data object Loading : AdminWalletDetailUiState
    data class Success(val transactions: List<WalletTransaction>) : AdminWalletDetailUiState
    data class Error(val message: String) : AdminWalletDetailUiState
}

@Composable
fun IComponent.AdminWalletsScreen() {
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings

    var page by remember { mutableStateOf(0) }
    var selectedWallet by remember { mutableStateOf<Wallet?>(null) }
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    val uiState by produceState<AdminWalletsUiState>(
        initialValue = AdminWalletsUiState.Loading,
        key1 = page
    ) {
        value = AdminWalletsUiState.Loading
        adminService.getWallets(page, PAGE_SIZE)
            .onRight { value = AdminWalletsUiState.Success(it) }
            .onLeft { value = AdminWalletsUiState.Error(it.localizedMessage(currentStrings)) }
    }

    if (selectedWallet != null) {
        AdminWalletDetailPanel(
            wallet = selectedWallet!!,
            onBack = { selectedWallet = null },
            onAdjusted = { updatedWallet ->
                selectedWallet = updatedWallet
                toastData = ToastData(currentStrings.walletCreditIssued, ToastType.Success)
            },
            onError = { msg ->
                toastData = ToastData(currentStrings.errorToast(msg), ToastType.Error)
            }
        )
    } else {
        div(className = "flex flex-col gap-6 animate-fade-in") {

            // --- 1. HEADER ---
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.adminWallets }
            }

            // --- 2. TABLE ---
            when (val state = uiState) {
                is AdminWalletsUiState.Loading -> Loading()
                is AdminWalletsUiState.Error -> {
                    div(className = "alert alert-error") {
                        span(className = "icon-[heroicons--x-circle] size-6")
                        span { +state.message }
                    }
                }
                is AdminWalletsUiState.Success -> {
                    val data = state.data
                    val totalPages = maxOf(1, ceil(data.totalCount.toDouble() / PAGE_SIZE).toInt())

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
                                                    // Code
                                                    td {
                                                        div(className = "font-mono font-bold text-sm") { +wallet.code }
                                                    }
                                                    // Owner email
                                                    td {
                                                        div(className = "text-sm") { +wallet.ownerEmail }
                                                    }
                                                    // Balance
                                                    td {
                                                        val balanceClass = if (wallet.balance > 0) "text-success font-bold" else "text-base-content/60"
                                                        div(className = balanceClass) {
                                                            +"${wallet.balance.toInt()} ${currentStrings.currency}"
                                                        }
                                                    }
                                                    // Created at
                                                    td {
                                                        div(className = "text-xs text-base-content/60") {
                                                            +wallet.createdAt
                                                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                                                .humanReadable
                                                        }
                                                    }
                                                    // Actions
                                                    td(className = "text-right") {
                                                        button(className = "btn btn-ghost btn-xs") {
                                                            onClick { selectedWallet = wallet }
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

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}

@Composable
private fun IComponent.AdminWalletDetailPanel(
    wallet: Wallet,
    onBack: () -> Unit,
    onAdjusted: (Wallet) -> Unit,
    onError: (String) -> Unit,
) {
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val settingsService = getService<AppSettingsServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings

    var adjustAmount by remember { mutableStateOf<Number?>(null) }
    var adjustNote by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var currentWallet by remember { mutableStateOf(wallet) }
    var resetDateLabel by remember { mutableStateOf<String?>(null) }

    val detailState by produceState<AdminWalletDetailUiState>(
        initialValue = AdminWalletDetailUiState.Loading,
        key1 = currentWallet.id
    ) {
        value = AdminWalletDetailUiState.Loading
        adminService.getWalletTransactions(currentWallet.id.toString())
            .onRight { value = AdminWalletDetailUiState.Success(it) }
            .onLeft { value = AdminWalletDetailUiState.Error(it.localizedMessage(currentStrings)) }
        settingsService.getSettings()
            .onRight { dto ->
                resetDateLabel = "${dto.seasonResetDay}. ${dto.seasonResetMonth}."
            }
    }

    fun doAdjust(isCredit: Boolean) {
        val amount = adjustAmount?.toDouble()
        if (amount == null || amount <= 0) return
        if (adjustNote.isBlank()) return
        isSubmitting = true
        scope.launch {
            adminService.adjustWalletBalance(currentWallet.id.toString(), amount, adjustNote, isCredit)
                .onRight { updated ->
                    currentWallet = updated
                    adjustAmount = null
                    adjustNote = ""
                    onAdjusted(updated)
                }
                .onLeft { error ->
                    onError(error.localizedMessage(currentStrings))
                }
            isSubmitting = false
        }
    }

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
                    span(className = "text-base-content/50") { +currentWallet.ownerEmail }
                    span(className = "font-bold text-success") {
                        +"${currentWallet.balance.toInt()} ${currentStrings.currency}"
                    }
                    resetDateLabel?.let { date ->
                        span(className = "text-xs text-base-content/50 flex items-center gap-1") {
                            span(className = "icon-[heroicons--calendar] size-3")
                            +"${currentStrings.walletExpiresOn}: $date"
                        }
                    }
                }
            }
        }

        CopyToClipboardButton(currentStrings.walletCode, currentWallet.code)

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
                            numeric(value = adjustAmount, min = 0, className = "input input-bordered w-full pr-12") {
                                onInput { adjustAmount = value }
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
                        text(value = adjustNote, className = "input input-bordered w-full") {
                            onInput { adjustNote = value ?: "" }
                        }
                    }
                }
                div(className = "flex gap-2 mt-4") {
                    button(className = "btn btn-success gap-2") {
                        disabled(isSubmitting || (adjustAmount?.toDouble() ?: 0.0) <= 0 || adjustNote.isBlank())
                        onClick { doAdjust(true) }
                        if (isSubmitting) span(className = "loading loading-spinner loading-sm")
                        span(className = "icon-[heroicons--plus] size-4")
                        +currentStrings.adminWalletCreditButton
                    }
                    button(className = "btn btn-error btn-outline gap-2") {
                        disabled(isSubmitting || (adjustAmount?.toDouble() ?: 0.0) <= 0 || adjustNote.isBlank())
                        onClick { doAdjust(false) }
                        if (isSubmitting) span(className = "loading loading-spinner loading-sm")
                        span(className = "icon-[heroicons--minus] size-4")
                        +currentStrings.adminWalletDebitButton
                    }
                }
            }
        }

        // --- TRANSACTION HISTORY ---
        when (val state = detailState) {
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
                            h2(className = "card-title text-lg") { +"Transakce" }
                        }
                        div(className = "overflow-x-auto") {
                            table(className = "table table-zebra w-full") {
                                thead {
                                    tr {
                                        th { +currentStrings.tableHeaderDate }
                                        th { +"Důvod" }
                                        th { +currentStrings.tableHeaderAmount }
                                        th { +"Poznámka" }
                                    }
                                }
                                tbody {
                                    if (state.transactions.isEmpty()) {
                                        tr {
                                            td {
                                                attribute("colspan", "4")
                                                div(className = "text-center text-base-content/50 py-8") {
                                                    +"Žádné transakce"
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
                                                        +"${if (tx.amount >= 0) "+" else ""}${tx.amount.toInt()} ${currentStrings.currency}"
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
}
