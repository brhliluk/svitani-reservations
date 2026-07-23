package cz.svitaninymburk.projects.reservations.ui.admin.events.instance

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.event.RecurrenceType
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowedPaymentsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.WaitlistCapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CustomFieldsBuilderSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.DurationField
import cz.svitaninymburk.projects.reservations.ui.admin.events.OwnerEmailsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.PriceCurrencyField
import cz.svitaninymburk.projects.reservations.ui.admin.events.ReservationDeadlineSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.ShowAttendeeCountCheckbox
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.html.*
import cz.svitaninymburk.projects.reservations.i18n.strings
import web.history.history
import web.html.HTMLSelectElement


@Composable
fun IComponent.AdminCreateEventInstanceScreen(currentUser: User, preselectedDefinitionId: String? = null) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember(preselectedDefinitionId) {
        buildAdminCreateEventInstanceModel(scope, router, currentUser.email, preselectedDefinitionId)
    }

    LaunchedEffect(preselectedDefinitionId) { model.load() }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

        // Hlavička
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { history.back() }
            }
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.newInstanceTitle }
                p(className = "text-base-content/60") { +currentStrings.newInstanceSubtitle }
            }
        }

        if (model.isLoadingDefinitions) {
            div(className = "flex justify-center p-10") {
                span(className = "loading loading-spinner loading-lg text-primary")
            }
        } else if (model.definitions.isEmpty()) {
            div(className = "alert alert-warning shadow-sm") {
                span(className = "icon-[heroicons--exclamation-triangle] size-6")
                div {
                    h3(className = "font-bold") { +currentStrings.noTemplatesHeading }
                    div(className = "text-sm") { +currentStrings.noTemplatesInstanceMessage }
                }
                button(className = "btn btn-sm btn-primary") {
                    onClick { router.navigate("/admin/events/new") }
                    +currentStrings.createTemplateButton
                }
            }
        } else {
            // --- VÝBĚR ŠABLONY A TERMÍNU ---
            div(className = "card bg-base-100 shadow-sm border-t-4 border-primary") {
                div(className = "card-body") {

                    div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                        // Výběr šablony
                        if (preselectedDefinitionId == null) {
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.templateSelectionLabel } }
                                select(className = "select select-bordered w-full text-base") {
                                    // Výchozí prázdná volba
                                    option(value = "", label = currentStrings.templatePlaceholder) {
                                        if (model.selectedDefinitionId == null) attribute("selected", "true")
                                        attribute("disabled", "true")
                                    }
                                    model.definitions.forEach { def ->
                                        option(value = def.id.toString(), label = def.title)
                                    }
                                    onChange { event ->
                                        model.onDefinitionSelected((event.target as? HTMLSelectElement)?.value)
                                    }
                                }
                            }
                        }

                        // Datum
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.dateLabelField } }
                            text(value = model.startDate, type = InputType.Date, className = "input input-bordered w-full") {
                                onInput { model.startDate = value ?: "" }
                            }
                        }

                        // Čas
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.timeLabelField } }
                            text(value = model.startTime, type = InputType.Time, className = "input input-bordered w-full") {
                                onInput { model.startTime = value ?: "" }
                            }
                        }

                        // Opakování
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-medium") { +currentStrings.recurrenceTypeLabel } }
                            select(className = "select select-bordered w-full") {
                                option(value = "NONE", label = currentStrings.recurrenceNone) { if (model.recurrenceType == RecurrenceType.NONE) selected(true) }
                                option(value = "DAILY", label = currentStrings.recurrenceDaily) { if (model.recurrenceType == RecurrenceType.DAILY) selected(true) }
                                option(value = "WEEKLY", label = currentStrings.recurrenceWeekly) { if (model.recurrenceType == RecurrenceType.WEEKLY) selected(true) }
                                option(value = "MONTHLY", label = currentStrings.recurrenceMonthly) { if (model.recurrenceType == RecurrenceType.MONTHLY) selected(true) }
                                onChange { event ->
                                    model.onRecurrenceTypeChanged(RecurrenceType.valueOf((event.target as? HTMLSelectElement)?.value ?: "NONE"))
                                }
                            }
                        }

                        if (model.recurrenceType != RecurrenceType.NONE) {
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.recurrenceEndLabel } }
                                text(value = model.recurrenceEndDateStr, type = InputType.Date, className = "input input-bordered w-full") {
                                    onInput { model.recurrenceEndDateStr = value ?: "" }
                                }
                            }
                        }
                    }
                }
            }

            // --- MOŽNOST PŘEPSÁNÍ HODNOT (Zobrazí se až po výběru šablony) ---
            if (model.selectedDefinitionId != null) {
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-2") { +currentStrings.overrideHeading }
                        p(className = "text-sm text-base-content/60 mb-4") {
                            +currentStrings.overrideDescription
                        }

                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            // Název
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.instanceTitleLabel } }
                                text(value = model.titleOverride, className = "input input-bordered w-full") {
                                    onInput { model.titleOverride = value ?: "" }
                                }
                            }

                            // Popis
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                                textArea(value = model.descriptionOverride, className = "textarea textarea-bordered h-24 w-full") {
                                    onInput { model.descriptionOverride = value ?: "" }
                                }
                            }

                            OwnerEmailsField(model.ownerEmails) { model.ownerEmails = it }

                            PriceCurrencyField(currentStrings.priceLabel, model.priceOverride) { model.priceOverride = it }

                            CapacityField(model.capacityOverride) { model.capacityOverride = it }

                            WaitlistCapacityField(model.waitlistCapacityOverride) { model.waitlistCapacityOverride = it }

                            DurationField(
                                currentStrings.durationLabel,
                                model.durationHours, model.durationMinutes,
                                { model.durationHours = it }, { model.durationMinutes = it },
                            )

                            AllowedPaymentsField(model.allowBankTransfer, model.allowOnSite, { model.allowBankTransfer = it }, { model.allowOnSite = it })

                            ShowAttendeeCountCheckbox(value = model.showAttendeeCount) { model.showAttendeeCount = it }

                        }
                    }
                }

                // --- NÁHLED OPAKOVÁNÍ ---
                if (model.isRecurring && model.startDate.isNotBlank() && model.startTime.isNotBlank()) {
                    div(className = "card bg-base-100 shadow-sm border border-primary/30") {
                        div(className = "card-body") {
                            div(className = "flex items-center gap-2 mb-3") {
                                span(className = "icon-[heroicons--calendar-days] size-5 text-primary")
                                h2(className = "card-title text-base") {
                                    +currentStrings.recurrencePreviewHeading(model.previewDates.size)
                                }
                            }
                            if (model.previewDates.isEmpty()) {
                                p(className = "text-warning text-sm") { +currentStrings.recurrencePreviewError }
                            } else {
                                div(className = "flex flex-wrap gap-2") {
                                    model.previewDates.forEach { dt ->
                                        span(className = "badge badge-outline badge-primary") {
                                            +dt.humanReadable
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- CUSTOM FIELDS BUILDER ---
                CustomFieldsBuilderSection(model.customFields) { model.customFields = it }

                // --- UZÁVĚRKA REZERVACÍ ---
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

                // --- ULOŽIT ---
                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") {
                        onClick { history.back() }
                        +currentStrings.cancel
                    }
                    button(className = "btn btn-outline") {
                        disabled(model.isSubmitting)
                        onClick { model.submit(false) }
                        +currentStrings.saveDraftButton
                    }
                    button(className = "btn btn-primary") {
                        disabled(model.isSubmitting)
                        onClick { model.submit(true) }
                        if (model.isSubmitting) span(className = "loading loading-spinner loading-sm")
                        span(className = "icon-[heroicons--check] size-5")
                        +(if (model.isRecurring && model.previewDates.size > 1) currentStrings.createInstancesButton(model.previewDates.size) else currentStrings.createInstanceButton)
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
