package cz.svitaninymburk.projects.reservations.ui.reservation.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.ReservationDetail
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.text.text
import dev.kilua.html.button
import dev.kilua.html.dialogRef
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.label
import dev.kilua.html.p
import dev.kilua.html.span
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid


@Composable
fun IComponent.ReservationDetailScreen(
    reservationId: Uuid,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableStateOf(0) }
    val currentStrings by strings

    val reservationService = getService<ReservationServiceInterface>(RpcSerializersModules)

    val uiState by produceState<ReservationLoadingUiState>(initialValue = ReservationLoadingUiState.Loading, key1 = refreshTrigger, key2 = reservationId) {
        value = ReservationLoadingUiState.Loading
        try {
            reservationService.getDetail(reservationId).fold(
                ifRight = { foundReservation -> value = ReservationLoadingUiState.Success(foundReservation) },
                ifLeft = { error -> value = ReservationLoadingUiState.Error(error.localizedMessage(currentStrings)) }
            )
        } catch (e: Exception) {
            value = ReservationLoadingUiState.Error(currentStrings.loadingError(e.message ?: "unknown"))
        }
    }

    var walletCode by remember { mutableStateOf("") }
    var showEmailMismatchWarning by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var cancelErrorMessage by remember { mutableStateOf<String?>(null) }
    var dialogPaidAmount by remember { mutableStateOf(0.0) }
    var dialogStartDateTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    val confirmDialog = dialogRef(className = "modal") {
        div(className = "modal-box flex flex-col gap-4") {
            h3(className = "font-bold text-lg text-error flex items-center gap-2") {
                span(className = "icon-[heroicons--exclamation-triangle] size-6")
                +currentStrings.cancelReservation
            }

            p(className = "text-base-content/70") { +currentStrings.cancelReservationConfirmBody }

            // Dynamic refund preview
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val cancellationDeadline = dialogStartDateTime?.date?.minus(1, DateTimeUnit.DAY)?.atTime(18, 0)
            val withinCancellationWindow = cancellationDeadline != null && now < cancellationDeadline

            when {
                dialogPaidAmount > 0.0 && withinCancellationWindow -> {
                    div(className = "alert alert-success py-2 px-3") {
                        span(className = "icon-[heroicons--check-circle] size-5 flex-shrink-0")
                        span(className = "text-sm") {
                            +currentStrings.cancellationRefundEligible("${dialogPaidAmount.toInt()}")
                        }
                    }
                }
                dialogPaidAmount > 0.0 -> {
                    div(className = "alert alert-warning py-2 px-3") {
                        span(className = "icon-[heroicons--exclamation-triangle] size-5 flex-shrink-0")
                        span(className = "text-sm") { +currentStrings.cancellationWindowPassed }
                    }
                }
                else -> {
                    div(className = "alert alert-info py-2 px-3") {
                        span(className = "icon-[heroicons--information-circle] size-5 flex-shrink-0")
                        span(className = "text-sm") { +currentStrings.cancellationNotPaid }
                    }
                }
            }

            // Wallet code input — only relevant when a refund will be issued
            if (dialogPaidAmount > 0.0 && withinCancellationWindow) {
                div(className = "form-control w-full") {
                    label(className = "label pb-1") {
                        span(className = "label-text") { +currentStrings.walletCode }
                    }
                    text(value = walletCode, className = "input input-bordered w-full") {
                        placeholder(currentStrings.walletCodePlaceholder)
                        onInput { walletCode = value ?: "" }
                    }
                    label(className = "label pt-1") {
                        span(className = "label-text-alt text-base-content/50") { +currentStrings.walletAutoCreate }
                    }
                }
            }

            // Email mismatch warning
            if (showEmailMismatchWarning) {
                div(className = "alert alert-warning") {
                    span(className = "icon-[heroicons--exclamation-triangle] size-5")
                    span { +currentStrings.walletEmailMismatchWarning }
                }
            }

            // Error message
            if (cancelErrorMessage != null) {
                div(className = "alert alert-error text-sm py-2") {
                    span(className = "icon-[heroicons--exclamation-circle] size-5")
                    span { +cancelErrorMessage!! }
                }
            }

            div(className = "modal-action") {
                button(className = "btn") {
                    disabled(isCancelling)
                    onClick {
                        this@dialogRef.element.close()
                        walletCode = ""
                        showEmailMismatchWarning = false
                        cancelErrorMessage = null
                    }
                    +currentStrings.cancel
                }

                if (showEmailMismatchWarning) {
                    button(className = "btn btn-warning text-white") {
                        disabled(isCancelling)
                        if (isCancelling) span(className = "loading loading-spinner loading-sm")
                        onClick {
                            isCancelling = true
                            cancelErrorMessage = null
                            scope.launch {
                                reservationService.cancelReservation(
                                    reservationId = reservationId,
                                    instanceId = null,
                                    walletCode = walletCode.ifBlank { null },
                                    force = true,
                                ).fold(
                                    ifRight = { result ->
                                        isCancelling = false
                                        walletCode = ""
                                        showEmailMismatchWarning = false
                                        this@dialogRef.element.close()
                                        val credit = result.walletCreditAmount
                                        if (credit != null && credit > 0.0) {
                                            toastData = ToastData(
                                                "${currentStrings.walletCreditIssued}: ${result.walletCode}",
                                                ToastType.Success
                                            )
                                        }
                                        refreshTrigger++
                                    },
                                    ifLeft = { error ->
                                        isCancelling = false
                                        cancelErrorMessage = error.localizedMessage(currentStrings)
                                    }
                                )
                            }
                        }
                        +currentStrings.walletEmailMismatchConfirm
                    }
                } else {
                    button(className = "btn btn-error text-white") {
                        disabled(isCancelling)
                        if (isCancelling) span(className = "loading loading-spinner loading-sm")
                        onClick {
                            isCancelling = true
                            cancelErrorMessage = null
                            scope.launch {
                                reservationService.cancelReservation(
                                    reservationId = reservationId,
                                    instanceId = null,
                                    walletCode = walletCode.ifBlank { null },
                                    force = false,
                                ).fold(
                                    ifRight = {
                                        isCancelling = false
                                        walletCode = ""
                                        showEmailMismatchWarning = false
                                        this@dialogRef.element.close()
                                        refreshTrigger++
                                    },
                                    ifLeft = { error ->
                                        isCancelling = false
                                        if (error is ReservationError.WalletEmailMismatch) {
                                            showEmailMismatchWarning = true
                                        } else {
                                            cancelErrorMessage = error.localizedMessage(currentStrings)
                                        }
                                    }
                                )
                            }
                        }
                        +currentStrings.cancelReservation
                    }
                }
            }
        }

        onClick { event ->
            if (event.target == this@dialogRef.element) {
                this@dialogRef.element.close()
                walletCode = ""
                showEmailMismatchWarning = false
                cancelErrorMessage = null
            }
        }
    }

    when (val state = uiState) {
        is ReservationLoadingUiState.Loading -> Loading()
        is ReservationLoadingUiState.Success -> {
            ReservationDetailLayout(
                reservation = state.detail.reservation,
                target = state.detail.target,
                accountNumber = state.detail.accountNumber,
                onCancelReservation = {
                    dialogPaidAmount = state.detail.reservation.paidAmount
                    dialogStartDateTime = state.detail.target?.startDateTime
                    confirmDialog.element.showModal()
                },
                onBackToDashboard = onBackClick,
            )
        }
        is ReservationLoadingUiState.Error -> {
            div(className = "min-h-screen flex items-center justify-center bg-base-200 p-4") {
                div(className = "card w-full max-w-md bg-base-100 shadow-xl") {
                    div(className = "card-body items-center text-center") {
                        div(className = "rounded-full bg-error/10 p-4 mb-2") {
                            span(className = "icon-[heroicons--exclamation-triangle] size-12 text-error")
                        }

                        h3(className = "card-title text-error") { +"Chyba načítání" }
                        p(className = "text-base-content/70 py-4") {
                            +state.message
                        }

                        div(className = "card-actions") {
                            button(className = "btn btn-primary") {
                                onClick { onBackClick() }
                                +"Zpět na přehled"
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

private sealed interface ReservationLoadingUiState {
    data object Loading : ReservationLoadingUiState
    data class Success(val detail: ReservationDetail) : ReservationLoadingUiState
    data class Error(val message: String) : ReservationLoadingUiState
}