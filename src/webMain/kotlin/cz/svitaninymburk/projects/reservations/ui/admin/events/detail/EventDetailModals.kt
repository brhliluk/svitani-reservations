package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.form
import dev.kilua.form.text.text
import dev.kilua.html.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

@Composable
fun IComponent.DeleteEventModal(
    isSeries: Boolean,
    reservationCount: Int,
    refundMoney: Boolean,
    onRefundMoneyChange: (Boolean) -> Unit,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentStrings by strings

    ConfirmModal(
        title = currentStrings.confirmDeleteTitle,
        confirmLabel = if (isSeries) currentStrings.deleteSeriesLabel else currentStrings.deleteEventLabel,
        dismissLabel = currentStrings.modalBack,
        isLoading = isLoading,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        titleClassName = "text-error",
        confirmClassName = "btn-error",
    ) {
        p(className = "py-4") { +currentStrings.deleteEventImpact(reservationCount) }
        RefundToggle(refundMoney, onRefundMoneyChange, toggleClassName = "toggle-error")
    }
}

@Composable
fun IComponent.CancelEventModal(
    reservationCount: Int,
    refundMoney: Boolean,
    onRefundMoneyChange: (Boolean) -> Unit,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentStrings by strings

    ConfirmModal(
        title = currentStrings.cancelEventConfirmTitle,
        confirmLabel = currentStrings.cancelEventLabel,
        dismissLabel = currentStrings.modalBack,
        isLoading = isLoading,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        titleClassName = "text-warning",
        confirmClassName = "btn-warning",
    ) {
        p(className = "py-4") { +currentStrings.cancelEventConfirmBody(reservationCount) }
        RefundToggle(refundMoney, onRefundMoneyChange, toggleClassName = "toggle-warning")
    }
}

@Composable
private fun IComponent.RefundToggle(
    refundMoney: Boolean,
    onRefundMoneyChange: (Boolean) -> Unit,
    toggleClassName: String,
) {
    val currentStrings by strings

    div(className = "form-control mt-2") {
        label(className = "label cursor-pointer justify-start gap-3") {
            checkBox(value = refundMoney, className = "toggle $toggleClassName") {
                onChange { onRefundMoneyChange(value) }
            }
            span(className = "label-text") { +currentStrings.refundOnCancelLabel }
        }
    }
}

@Composable
fun IComponent.CancelLessonModal(
    lesson: EventInstance,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentStrings by strings

    ConfirmModal(
        title = currentStrings.cancelLessonModalTitle,
        confirmLabel = currentStrings.cancelLessonButton,
        dismissLabel = currentStrings.cancel,
        isLoading = isLoading,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        p(className = "py-4") {
            +currentStrings.cancelLessonModalBody(lesson.startDateTime.date.humanReadable)
        }
    }
}

@Composable
fun IComponent.AddLessonModal(
    lastLesson: EventInstance?,
    inheritedCustomFields: List<CustomFieldDefinition>,
    isSubmitting: Boolean,
    onSubmit: (startDateTime: LocalDateTime, endDateTime: LocalDateTime, isDropIn: Boolean) -> Unit,
    onInvalidInput: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentStrings by strings

    var date by remember { mutableStateOf(lastLesson?.startDateTime?.date?.plus(7, DateTimeUnit.DAY)?.toString() ?: "") }
    var startTime by remember { mutableStateOf(lastLesson?.startDateTime?.time?.toInputValue() ?: "") }
    var endTime by remember { mutableStateOf(lastLesson?.endDateTime?.time?.toInputValue() ?: "") }
    var isDropIn by remember { mutableStateOf(false) }

    div(className = "modal modal-open") {
        div(className = "modal-box") {
            h3(className = "font-bold text-lg mb-4") { +currentStrings.addLessonModalTitle }
            div(className = "flex flex-col gap-3") {
                div(className = "form-control w-full") {
                    label(className = "label") { span(className = "label-text") { +currentStrings.addLessonDateLabel } }
                    text(value = date, type = InputType.Date, className = "input input-bordered w-full") { onInput { date = value ?: "" } }
                }
                div(className = "grid grid-cols-2 gap-3") {
                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text") { +currentStrings.addLessonStartLabel } }
                        text(value = startTime, type = InputType.Time, className = "input input-bordered w-full") { onInput { startTime = value ?: "" } }
                    }
                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text") { +currentStrings.addLessonEndLabel } }
                        text(value = endTime, type = InputType.Time, className = "input input-bordered w-full") { onInput { endTime = value ?: "" } }
                    }
                }
                label(className = "label cursor-pointer justify-start gap-2") {
                    checkBox(value = isDropIn, className = "checkbox checkbox-sm") { onClick { isDropIn = this.value } }
                    span(className = "label-text") { +currentStrings.addLessonDropInLabel }
                }
                if (inheritedCustomFields.isNotEmpty()) {
                    div(className = "alert alert-info text-sm flex flex-col items-start gap-1") {
                        span { +currentStrings.addLessonInheritedFieldsNote }
                        ul(className = "list-disc list-inside") {
                            inheritedCustomFields.forEach { field ->
                                li { +field.label }
                            }
                        }
                    }
                }
            }
            div(className = "modal-action") {
                button(className = "btn") { disabled(isSubmitting); onClick { onDismiss() }; +currentStrings.cancel }
                button(className = "btn btn-primary") {
                    disabled(isSubmitting)
                    onClick {
                        val parsedDate = try { LocalDate.parse(date) } catch (_: Exception) { null }
                        val parsedStart = try { LocalTime.parse(startTime) } catch (_: Exception) { null }
                        val parsedEnd = try { LocalTime.parse(endTime) } catch (_: Exception) { null }
                        if (parsedDate == null || parsedStart == null || parsedEnd == null || parsedEnd <= parsedStart) {
                            onInvalidInput()
                            return@onClick
                        }
                        onSubmit(LocalDateTime(parsedDate, parsedStart), LocalDateTime(parsedDate, parsedEnd), isDropIn)
                    }
                    if (isSubmitting) span(className = "loading loading-spinner loading-sm")
                    +currentStrings.saveChanges
                }
            }
        }
        form(className = "modal-backdrop") {
            button { onClick { if (!isSubmitting) onDismiss() }; +currentStrings.close }
        }
    }
}

private fun LocalTime.toInputValue(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
