package cz.svitaninymburk.projects.reservations.ui.admin.reservations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import dev.kilua.core.IComponent
import dev.kilua.html.p
import dev.kilua.html.strong
import kotlin.uuid.Uuid

enum class AdminActionType { CONFIRM_PAYMENT, CANCEL_RESERVATION }

data class PendingAction(val type: AdminActionType, val reservationId: Uuid, val participantName: String)

@Composable
fun IComponent.ReservationActionModal(
    action: PendingAction,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentStrings by strings
    val isPaymentConfirmation = action.type == AdminActionType.CONFIRM_PAYMENT

    ConfirmModal(
        title = if (isPaymentConfirmation) currentStrings.modalConfirmPaymentTitle else currentStrings.modalCancelReservationTitle,
        confirmLabel = if (isPaymentConfirmation) currentStrings.modalConfirmAction else currentStrings.modalConfirmCancelAction,
        dismissLabel = currentStrings.modalBack,
        isLoading = isLoading,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmClassName = if (isPaymentConfirmation) "btn-success" else "btn-error",
    ) {
        p(className = "py-4") {
            if (isPaymentConfirmation) {
                +currentStrings.modalConfirmPaymentMsgPre
                strong { +action.participantName }
                +currentStrings.modalConfirmPaymentMsgPost
            } else {
                +currentStrings.modalCancelMsgPre
                strong { +action.participantName }
                +currentStrings.modalCancelMsgPost
            }
        }
    }
}
