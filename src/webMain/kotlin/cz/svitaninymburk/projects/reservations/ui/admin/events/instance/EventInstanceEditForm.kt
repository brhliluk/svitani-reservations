package cz.svitaninymburk.projects.reservations.ui.admin.events.instance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowedPaymentsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CustomFieldsBuilderSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.DurationField
import cz.svitaninymburk.projects.reservations.ui.admin.events.OwnerEmailsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.PriceCurrencyField
import cz.svitaninymburk.projects.reservations.ui.admin.events.ReservationDeadlineSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowMultipleSeatsCheckbox
import cz.svitaninymburk.projects.reservations.ui.admin.events.ShowAttendeeCountCheckbox
import cz.svitaninymburk.projects.reservations.ui.admin.events.WaitlistCapacityField
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.h2
import dev.kilua.html.h3
import dev.kilua.html.label
import dev.kilua.html.p
import dev.kilua.html.span
import web.history.history

@Composable
fun IComponent.AdminEditEventInstanceScreen(id: String) {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember(id) { buildAdminEditEventInstanceModel(scope, id) }

    LaunchedEffect(id) { model.load() }

    when (val state = model.uiState) {
        is EditInstanceUiState.Loading -> Loading()
        is EditInstanceUiState.Error -> div(className = "alert alert-error max-w-lg mx-auto mt-10") { +state.message }
        is EditInstanceUiState.Loaded -> {
            div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {
                div(className = "flex items-center gap-4") {
                    button(className = "btn btn-circle btn-ghost btn-sm") {
                        span(className = "icon-[heroicons--arrow-left] size-5"); onClick { history.back() }
                    }
                    div(className = "flex-1") {
                        h1(className = "text-3xl font-bold text-base-content") { +currentStrings.editInstanceTitle }
                        p(className = "text-base-content/60") { +state.instance.title }
                    }
                    if (state.instance.isPublished) {
                        span(className = "badge badge-primary badge-sm") { +currentStrings.statusPublished }
                    } else {
                        span(className = "badge badge-ghost badge-sm") { +currentStrings.statusHidden }
                    }
                    button(className = if (state.instance.isPublished) "btn btn-sm btn-outline" else "btn btn-sm btn-primary") {
                        onClick { model.requestTogglePublished() }
                        +if (state.instance.isPublished) currentStrings.hideButton else currentStrings.publishButton
                    }
                }
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-4") { +currentStrings.basicInfoHeading }
                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.instanceTitleLabel } }
                                text(value = model.title, className = "input input-bordered w-full") { onInput { model.title = value ?: "" } }
                            }
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                                textArea(value = model.description, className = "textarea textarea-bordered h-24 w-full") { onInput { model.description = value ?: "" } }
                            }
                            OwnerEmailsField(model.ownerEmails) { model.ownerEmails = it }

                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.dateLabelField } }
                                text(value = model.startDate, type = InputType.Date, className = "input input-bordered w-full") { onInput { model.startDate = value ?: "" } }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.timeLabelField } }
                                text(value = model.startTime, type = InputType.Time, className = "input input-bordered w-full") { onInput { model.startTime = value ?: "" } }
                            }

                            DurationField(
                                currentStrings.durationLabel,
                                model.durationHours, model.durationMinutes,
                                { model.durationHours = it }, { model.durationMinutes = it },
                            )

                            PriceCurrencyField(currentStrings.defaultPriceLabel, model.price) { model.price = it }

                            CapacityField(model.capacity) { model.capacity = it }

                            WaitlistCapacityField(model.waitlistCapacity) { model.waitlistCapacity = it }

                            AllowedPaymentsField(model.allowBankTransfer, model.allowOnSite, { model.allowBankTransfer = it }, { model.allowOnSite = it })

                            ShowAttendeeCountCheckbox(value = model.showAttendeeCount) { model.showAttendeeCount = it }
                            AllowMultipleSeatsCheckbox(value = model.allowMultipleSeats) { model.allowMultipleSeats = it }
                        }
                    }
                }
                if (state.instance.seriesId != null) {
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body") {
                            label(className = "cursor-pointer flex items-center gap-3") {
                                checkBox(value = model.isDropIn, className = "checkbox checkbox-secondary") {
                                    onChange { model.isDropIn = value }
                                }
                                div {
                                    span(className = "font-medium") { +currentStrings.dropInLabel }
                                    p(className = "text-sm text-base-content/60 mt-1") { +currentStrings.dropInDescription }
                                }
                            }
                        }
                    }
                }
                CustomFieldsBuilderSection(model.customFields) { model.customFields = it }

                ReservationDeadlineSection(
                    enabled = model.deadlineEnabled,
                    typeIsHours = model.deadlineTypeIsHours,
                    hours = model.deadlineHours,
                    daysBefore = model.deadlineDaysBefore,
                    timeStr = model.deadlineTimeStr,
                    message = model.deadlineMessage,
                    onEnabledChange = { model.deadlineEnabled = it },
                    onTypeChange = { model.deadlineTypeIsHours = it },
                    onHoursChange = { model.deadlineHours = it },
                    onDaysBeforeChange = { model.deadlineDaysBefore = it },
                    onTimeStrChange = { model.deadlineTimeStr = it },
                    onMessageChange = { model.deadlineMessage = it },
                )
                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") { onClick { history.back() }; +currentStrings.cancel }
                    button(className = "btn btn-primary") {
                        disabled(model.isSubmitting)
                        onClick { model.submit() }
                        if (model.isSubmitting) span(className = "loading loading-spinner loading-sm")
                        span(className = "icon-[heroicons--check] size-5")
                        +currentStrings.saveChanges
                    }
                }
            }
        }
    }

    if (model.showCapacityWarning) {
        div(className = "modal modal-open") {
            div(className = "modal-box") {
                h3(className = "font-bold text-lg") { +currentStrings.capacityWarningTitle }
                p(className = "py-4") { +currentStrings.capacityWarningBody }
                div(className = "modal-action") {
                    button(className = "btn") { onClick { model.dismissCapacityWarning() }; +currentStrings.cancel }
                    button(className = "btn btn-warning") { onClick { model.confirmCapacityWarning() }; +currentStrings.saveChanges }
                }
            }
        }
    }

    if (model.showHideConfirm) {
        div(className = "modal modal-open") {
            div(className = "modal-box") {
                h3(className = "font-bold text-lg") { +currentStrings.hideButton }
                p(className = "py-4") { +currentStrings.hideWithReservationsConfirm }
                div(className = "modal-action") {
                    button(className = "btn") { onClick { model.dismissHideConfirm() }; +currentStrings.cancel }
                    button(className = "btn btn-warning") { onClick { model.confirmHide() }; +currentStrings.hideButton }
                }
            }
        }
    }

    Toast(message = model.toast?.message, type = model.toast?.type ?: ToastType.Success, onDismiss = { model.dismissToast() })
}
