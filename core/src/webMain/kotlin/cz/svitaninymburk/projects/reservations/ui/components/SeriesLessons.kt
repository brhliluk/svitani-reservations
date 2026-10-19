package cz.svitaninymburk.projects.reservations.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonItem
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonsView
import cz.svitaninymburk.projects.reservations.ui.components.usecase.LessonRefundPreview
import cz.svitaninymburk.projects.reservations.ui.components.usecase.canOptOut
import cz.svitaninymburk.projects.reservations.ui.components.usecase.lessonRefundAmount
import cz.svitaninymburk.projects.reservations.ui.components.usecase.lessonRefundRate
import cz.svitaninymburk.projects.reservations.ui.components.usecase.lessonRefundPreview
import cz.svitaninymburk.projects.reservations.ui.components.usecase.needsWalletCodeInput
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.form.text.text
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.dialogRef
import dev.kilua.html.h3
import dev.kilua.html.label
import dev.kilua.html.p
import dev.kilua.html.span
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Termíny kurzu s možností odhlásit se z jednotlivé lekce. Stejná sekce slouží
 * přihlášenému uživateli v „Moje rezervace" i hostovi na detailu rezervace —
 * o tom, jestli se ptát na kód peněženky, rozhoduje [SeriesLessonsView], ne to,
 * kdo se zrovna dívá.
 */
@Composable
fun IComponent.SeriesLessonsSection(
    reservationId: Uuid,
    view: SeriesLessonsView,
    onLessonsChanged: () -> Unit,
) {
    val currentStrings by strings
    val scope = rememberCoroutineScope()
    val model = remember(reservationId) { buildLessonOptOutModel(scope, reservationId) }

    val optOutDialog = dialogRef(className = "modal") {
        div(className = "modal-box flex flex-col gap-3") {
            h3(className = "font-bold text-lg text-warning flex items-center gap-2") {
                span(className = "icon-[heroicons--exclamation-triangle] size-6")
                +currentStrings.lessonOptOutConfirmTitle
            }

            val lesson = model.pendingLesson
            if (lesson != null) {
                p { +currentStrings.lessonOptOutConfirmBody(lesson.startDateTime.humanReadable) }

                val now = Clock.System.now()
                val preview = lessonRefundPreview(view, lesson, now)
                when (preview) {
                    LessonRefundPreview.REFUND_ELIGIBLE -> {
                        div(className = "alert alert-success py-2 px-3") {
                            span(className = "icon-[heroicons--check-circle] size-5 flex-shrink-0")
                            div(className = "text-sm flex flex-col") {
                                span { +currentStrings.lessonOptOutCreditInfo("${lessonRefundAmount(view).toInt()}") }
                                // Uzávěrka přišla ze serveru; ukazuje se v čase návštěvníka.
                                // Odlišuje se velikostí, ne barvou — text-base-content
                                // je v tmavém tématu světlý a na zeleném alertu zmizí.
                                lesson.optOutDeadline?.let { deadline ->
                                    span(className = "text-xs opacity-90") {
                                        +currentStrings.lessonOptOutDeadlineInfo(
                                            deadline.toLocalDateTime(TimeZone.currentSystemDefault()).humanReadable
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LessonRefundPreview.WINDOW_PASSED -> {
                        div(className = "alert alert-warning py-2 px-3") {
                            span(className = "icon-[heroicons--exclamation-triangle] size-5 flex-shrink-0")
                            span(className = "text-sm") { +currentStrings.cancellationWindowPassed }
                        }
                    }
                    LessonRefundPreview.NOT_PAID -> {
                        div(className = "alert alert-info py-2 px-3") {
                            span(className = "icon-[heroicons--information-circle] size-5 flex-shrink-0")
                            div(className = "text-sm flex flex-col") {
                                span { +currentStrings.lessonOptOutCreditAfterPayment("${lessonRefundRate(view).toInt()}") }
                                lesson.optOutDeadline?.let { deadline ->
                                    span(className = "text-xs opacity-90") {
                                        +currentStrings.lessonOptOutDeadlineInfo(
                                            deadline.toLocalDateTime(TimeZone.currentSystemDefault()).humanReadable
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LessonRefundPreview.NO_REFUND_CONFIGURED -> {
                        div(className = "alert alert-info py-2 px-3") {
                            span(className = "icon-[heroicons--information-circle] size-5 flex-shrink-0")
                            span(className = "text-sm") { +currentStrings.cancellationNoRefund }
                        }
                    }
                }

                // Omluvenku dnes nejde vzít zpět ani přes admina — musí to zaznít předem.
                p(className = "text-sm text-base-content/70") { +currentStrings.lessonOptOutIrreversible }

                if (needsWalletCodeInput(view.isAnonymousReservation, preview)) {
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
            }

            if (model.showEmailMismatchWarning) {
                div(className = "alert alert-warning") {
                    span(className = "icon-[heroicons--exclamation-triangle] size-5")
                    span { +currentStrings.walletEmailMismatchWarning }
                }
            }

            model.errorMessage?.let { message ->
                div(className = "alert alert-error text-sm py-2") {
                    span(className = "icon-[heroicons--exclamation-circle] size-5")
                    span { +message }
                }
            }

            div(className = "modal-action") {
                button(className = "btn") {
                    disabled(model.isSubmitting)
                    onClick {
                        this@dialogRef.element.close()
                        model.dismiss()
                    }
                    +currentStrings.cancel
                }

                val force = model.showEmailMismatchWarning
                button(className = "btn btn-warning text-white") {
                    disabled(model.isSubmitting)
                    if (model.isSubmitting) span(className = "loading loading-spinner loading-sm")
                    onClick {
                        model.confirm(force = force) {
                            this@dialogRef.element.close()
                            onLessonsChanged()
                        }
                    }
                    +if (force) currentStrings.walletEmailMismatchConfirm else currentStrings.lessonOptOut
                }
            }
        }

        onClick { event ->
            if (event.target == this@dialogRef.element) {
                this@dialogRef.element.close()
                model.dismiss()
            }
        }
    }

    div(className = "flex flex-col gap-2") {
        if (view.lessons.isEmpty()) {
            p(className = "text-sm text-base-content/50 italic py-2") { +currentStrings.noLessonsYet }
        } else {
            view.lessons.forEach { lesson ->
                LessonRow(
                    lesson = lesson,
                    onOptOutClick = {
                        model.startOptOut(lesson)
                        optOutDialog.element.showModal()
                    },
                )
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
internal fun IComponent.LessonRow(lesson: SeriesLessonItem, onOptOutClick: () -> Unit) {
    val currentStrings by strings

    div(className = "flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-2 rounded-lg bg-base-200/50 text-sm") {
        div(className = "flex items-center gap-2 text-base-content/70") {
            span(className = "icon-[heroicons--clock] size-4 shrink-0")
            span { +lesson.startDateTime.humanReadable }
            when {
                lesson.isCancelled -> div(className = "badge badge-error badge-sm gap-1") {
                    +currentStrings.lessonCancelledBadge
                }
                lesson.isOptedOut -> {
                    div(className = "badge badge-warning badge-sm gap-1") {
                        +currentStrings.lessonOptedOut
                    }
                    if (lesson.isLateCancellation) {
                        div(className = "badge badge-ghost badge-sm gap-1") {
                            +currentStrings.lessonOptOutLate
                        }
                    }
                }
                else -> {}
            }
        }

        if (canOptOut(lesson, Clock.System.now())) {
            button(className = "btn btn-outline btn-warning btn-xs gap-1 shrink-0") {
                span(className = "icon-[heroicons--arrow-left-end-on-rectangle] size-3")
                +currentStrings.lessonOptOut
                onClick { onOptOutClick() }
            }
        }
    }
}
