package cz.svitaninymburk.projects.reservations.ui.admin.reservations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.StartedCourseCancellation
import cz.svitaninymburk.projects.reservations.ui.util.AMOUNT_INPUT_LOCALE
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import cz.svitaninymburk.projects.reservations.ui.util.formatAmount
import dev.kilua.core.IComponent
import dev.kilua.form.number.numeric
import dev.kilua.html.div
import dev.kilua.html.label
import dev.kilua.html.p
import dev.kilua.html.span
import dev.kilua.html.strong
import kotlin.uuid.Uuid

/**
 * Admin ruší rezervaci na už rozběhnutý kurz. [refundInput] začíná na návrhu
 * serveru a admin ho může přepsat na libovolnou nezápornou částku.
 */
data class StartedCourseCancelDraft(
    val reservationId: Uuid,
    val participantName: String,
    val preview: StartedCourseCancellation,
    val refundInput: Number? = preview.suggestedRefund,
)

/** Částka z pole, nebo null, když se s ní rušit nedá (prázdné, záporné, nečíslo). */
fun refundAmountOrNull(input: Number?): Double? =
    input?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 }

@Composable
fun IComponent.CancelStartedCourseModal(
    draft: StartedCourseCancelDraft,
    isLoading: Boolean,
    onRefundChange: (Number?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentStrings by strings
    val preview = draft.preview

    ConfirmModal(
        title = currentStrings.modalCancelStartedCourseTitle,
        confirmLabel = currentStrings.modalConfirmCancelAction,
        dismissLabel = currentStrings.modalBack,
        isLoading = isLoading,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmEnabled = refundAmountOrNull(draft.refundInput) != null,
    ) {
        p(className = "py-4") {
            +currentStrings.modalCancelStartedCourseMsgPre
            strong { +draft.participantName }
            +currentStrings.modalCancelStartedCourseMsgPost
        }

        div(className = "text-sm space-y-1 mb-4") {
            BreakdownRow(currentStrings.startedCoursePaidLabel, formatAmount(preview.paidAmount, currentStrings))
            if (preview.alreadyRefunded > 0.0) {
                BreakdownRow(currentStrings.startedCourseAlreadyRefundedLabel, "−" + formatAmount(preview.alreadyRefunded, currentStrings))
            }
            BreakdownRow(
                currentStrings.startedCourseAttendedLabel(preview.attendedLessons, formatAmount(preview.lessonCredit, currentStrings)),
                "−" + formatAmount(preview.attendedLessons * preview.lessonCredit, currentStrings),
            )
            div(className = "flex justify-between font-semibold border-t border-base-300 pt-1") {
                span { +currentStrings.startedCourseSuggestedLabel }
                span { +formatAmount(preview.suggestedRefund, currentStrings) }
            }
        }

        div(className = "form-control w-full") {
            label(className = "label") {
                span(className = "label-text font-medium") { +currentStrings.refundAmountLabel }
            }
            div(className = "relative flex items-center") {
                numeric(value = draft.refundInput, min = 0, decimals = 0, locale = AMOUNT_INPUT_LOCALE, className = "input input-bordered w-full pr-12") {
                    onInput { onRefundChange(value) }
                    onChange { onRefundChange(value) }
                }
                span(className = "absolute right-4 text-base-content/50 font-medium") { +currentStrings.currency }
            }
            span(className = "label-text-alt text-base-content/60 mt-1") { +currentStrings.refundAmountHint }
        }
    }
}

@Composable
private fun IComponent.BreakdownRow(label: String, value: String) {
    div(className = "flex justify-between") {
        span(className = "text-base-content/70") { +label }
        span { +value }
    }
}
