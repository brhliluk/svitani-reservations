package cz.svitaninymburk.projects.reservations.ui.reservation.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.components.SeriesLessonsSection
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.usecase.CancellationPreview
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.usecase.cancellationPreview
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
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
import kotlin.time.Clock
import kotlin.uuid.Uuid


@Composable
fun IComponent.ReservationDetailScreen(
    reservationId: Uuid,
    onBackClick: () -> Unit
) {
    val currentStrings by strings
    val scope = rememberCoroutineScope()
    val model = remember(reservationId) { buildReservationDetailModel(scope, reservationId) }

    LaunchedEffect(reservationId) { model.load() }

    val detail = (model.uiState as? ReservationDetailUiState.Success)?.detail
    val paidAmount = detail?.reservation?.paidAmount ?: 0.0
    val preview = cancellationPreview(paidAmount, detail?.cancellationDeadline, Clock.System.now())

    val confirmDialog = dialogRef(className = "modal") {
        div(className = "modal-box flex flex-col gap-4") {
            h3(className = "font-bold text-lg text-error flex items-center gap-2") {
                span(className = "icon-[heroicons--exclamation-triangle] size-6")
                +currentStrings.cancelReservation
            }

            p(className = "text-base-content/70") { +currentStrings.cancelReservationConfirmBody }

            when (preview) {
                CancellationPreview.REFUND_ELIGIBLE -> {
                    div(className = "alert alert-success py-2 px-3") {
                        span(className = "icon-[heroicons--check-circle] size-5 flex-shrink-0")
                        span(className = "text-sm") {
                            +currentStrings.cancellationRefundEligible("${paidAmount.toInt()}")
                        }
                    }
                }
                CancellationPreview.WINDOW_PASSED -> {
                    div(className = "alert alert-warning py-2 px-3") {
                        span(className = "icon-[heroicons--exclamation-triangle] size-5 flex-shrink-0")
                        span(className = "text-sm") { +currentStrings.cancellationWindowPassed }
                    }
                }
                CancellationPreview.NOT_PAID -> {
                    div(className = "alert alert-info py-2 px-3") {
                        span(className = "icon-[heroicons--information-circle] size-5 flex-shrink-0")
                        span(className = "text-sm") { +currentStrings.cancellationNotPaid }
                    }
                }
            }

            // Kód peněženky má smysl jen tam, kde kredit reálně vznikne.
            if (preview == CancellationPreview.REFUND_ELIGIBLE) {
                div(className = "form-control w-full") {
                    label(className = "label pb-1") {
                        span(className = "label-text") { +currentStrings.walletCode }
                    }
                    text(value = model.walletCode, className = "input input-bordered w-full") {
                        placeholder(currentStrings.walletCodePlaceholder)
                        onInput { model.setWalletCode(value ?: "") }
                    }
                    // Ne label — daisyUI 5 mu dává white-space: nowrap, takže by
                    // se tahle věta nezalomila a vytekla by z dialogu.
                    p(className = "text-xs text-base-content/60 pt-1") {
                        +currentStrings.walletAutoCreate
                    }
                }
            }

            if (model.showEmailMismatchWarning) {
                div(className = "alert alert-warning") {
                    span(className = "icon-[heroicons--exclamation-triangle] size-5")
                    span { +currentStrings.walletEmailMismatchWarning }
                }
            }

            model.cancelErrorMessage?.let { message ->
                div(className = "alert alert-error text-sm py-2") {
                    span(className = "icon-[heroicons--exclamation-circle] size-5")
                    span { +message }
                }
            }

            div(className = "modal-action") {
                button(className = "btn") {
                    disabled(model.isCancelling)
                    onClick {
                        this@dialogRef.element.close()
                        model.resetCancelDialog()
                    }
                    +currentStrings.cancel
                }

                val force = model.showEmailMismatchWarning
                button(className = "btn ${if (force) "btn-warning" else "btn-error"} text-white") {
                    disabled(model.isCancelling)
                    if (model.isCancelling) span(className = "loading loading-spinner loading-sm")
                    onClick {
                        model.cancelWholeReservation(force = force) { this@dialogRef.element.close() }
                    }
                    +if (force) currentStrings.walletEmailMismatchConfirm else currentStrings.cancelReservation
                }
            }
        }

        onClick { event ->
            if (event.target == this@dialogRef.element) {
                this@dialogRef.element.close()
                model.resetCancelDialog()
            }
        }
    }

    when (val state = model.uiState) {
        is ReservationDetailUiState.Loading -> Loading()
        is ReservationDetailUiState.Success -> {
            ReservationDetailLayout(
                reservation = state.detail.reservation,
                target = state.detail.target,
                accountNumber = state.detail.accountNumber,
                waitlistPosition = state.detail.waitlistPosition,
                onCancelReservation = { confirmDialog.element.showModal() },
                onBackToDashboard = onBackClick,
                canBeClaimed = state.detail.claimable,
                isClaiming = model.isClaiming,
                onClaimReservation = { model.claimReservation() },
                hasLessons = state.lessons != null,
                lessonsSlot = state.lessons?.let { lessons ->
                    {
                        SeriesLessonsSection(
                            reservationId = reservationId,
                            view = lessons,
                            onLessonsChanged = { model.reloadLessons() },
                        )
                    }
                } ?: {},
            )
        }
        is ReservationDetailUiState.Error -> {
            div(className = "min-h-screen flex items-center justify-center bg-base-200 p-4") {
                div(className = "card w-full max-w-md bg-base-100 shadow-xl") {
                    div(className = "card-body items-center text-center") {
                        div(className = "rounded-full bg-error/10 p-4 mb-2") {
                            span(className = "icon-[heroicons--exclamation-triangle] size-12 text-error")
                        }

                        h3(className = "card-title text-error") { +currentStrings.errorLoadingTitle }
                        p(className = "text-base-content/70 py-4") {
                            +state.message
                        }

                        div(className = "card-actions") {
                            button(className = "btn btn-primary") {
                                onClick { onBackClick() }
                                +currentStrings.backToDashboard
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
