package cz.svitaninymburk.projects.reservations.slot.ui.admin

import androidx.compose.runtime.Composable
import cz.svitaninymburk.projects.reservations.slot.SLOT_STEPS
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotAdminStrings
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowedPaymentsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CustomFieldsBuilderSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.OwnerEmailsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.PriceCurrencyField
import dev.kilua.core.IComponent
import dev.kilua.form.number.numeric
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h2
import dev.kilua.html.label
import dev.kilua.html.option
import dev.kilua.html.p
import dev.kilua.html.span
import web.html.HTMLSelectElement

@Composable
internal fun IComponent.SettingsTab(model: SlotSettingsModel) {
    val s = slotAdminStrings
    val form = model.form

    div(className = "flex flex-col gap-6") {
        div(className = "$ADMIN_CARD p-6 grid grid-cols-1 md:grid-cols-2 gap-4") {
            label(className = "form-control w-full md:col-span-2 flex flex-col gap-1") {
                div(className = "label") { span(className = "label-text font-medium") { +s.titleLabel } }
                text(value = form.title, className = "input input-bordered w-full") {
                    onInput { model.edit { it.copy(title = value.orEmpty()) } }
                }
            }
            label(className = "form-control w-full md:col-span-2 flex flex-col gap-1") {
                div(className = "label") { span(className = "label-text font-medium") { +s.descriptionLabel } }
                textArea(value = form.description, className = "textarea textarea-bordered h-24 w-full") {
                    onInput { model.edit { it.copy(description = value.orEmpty()) } }
                }
            }

            label(className = "form-control w-full flex flex-col gap-1") {
                div(className = "label") { span(className = "label-text font-medium") { +s.stepLabel } }
                select(className = "select select-bordered w-full") {
                    SLOT_STEPS.forEach { step ->
                        val minutes = step.inWholeMinutes
                        option(minutes.toString(), label = s.stepOption(minutes)) {
                            if (minutes == form.stepMinutes) selected(true)
                        }
                    }
                    onChange { event ->
                        val minutes = (event.target as? HTMLSelectElement)?.value?.toLongOrNull() ?: return@onChange
                        model.edit { it.copy(stepMinutes = minutes) }
                    }
                }
            }
            PriceCurrencyField(label = s.priceLabel, value = form.pricePerHour) { value ->
                model.edit { it.copy(pricePerHour = value?.toDouble()) }
            }
            NumberField(s.minDurationLabel, form.minMinutes, s.minutesSuffix, hint = s.minDurationFieldHint) { v -> model.edit { it.copy(minMinutes = v) } }
            NumberField(s.maxDurationLabel, form.maxMinutes, s.minutesSuffix, hint = s.maxDurationFieldHint) { v -> model.edit { it.copy(maxMinutes = v) } }
            NumberField(s.capacityLabel, form.capacity) { v -> model.edit { it.copy(capacity = v) } }
            NumberField(s.maxSeatsLabel, form.maxSeats) { v -> model.edit { it.copy(maxSeats = v) } }
            NumberField(s.deadlineLabel, form.deadlineHours, hint = s.deadlineHint) { v -> model.edit { it.copy(deadlineHours = v) } }
            NumberField(s.horizonLabel, form.horizonDays) { v -> model.edit { it.copy(horizonDays = v) } }

            AllowedPaymentsField(
                bankTransfer = form.bankTransfer,
                onSite = form.onSite,
                onBankTransferChange = { v -> model.edit { it.copy(bankTransfer = v) } },
                onOnSiteChange = { v -> model.edit { it.copy(onSite = v) } },
            )
            OwnerEmailsField(form.ownerEmails) { emails -> model.edit { it.copy(ownerEmails = emails) } }
        }

        div(className = "flex flex-col gap-2") {
            h2(className = "text-lg font-bold") { +s.formFieldsHeading }
            p(className = "text-sm text-base-content/60") { +s.formFieldsHint }
            CustomFieldsBuilderSection(form.customFields, allowTimeMultiplier = false) { fields ->
                model.edit { it.copy(customFields = fields) }
            }
        }

        if (model.problems.isNotEmpty()) {
            div(className = "alert alert-error") {
                attribute("role", "alert")
                span(className = "icon-[heroicons--exclamation-circle] size-6")
                div { model.problems.forEach { p { +s.serviceProblem(it) } } }
            }
        }

        button(className = "btn btn-primary self-start gap-2") {
            disabled(!model.canSave)
            onClick { model.save() }
            if (!model.canSave) span(className = "loading loading-spinner loading-sm")
            +if (model.isNew) s.continueToSchedule else s.save
        }
    }
}

@Composable
private fun IComponent.NumberField(label: String, value: Int, suffix: String? = null, hint: String? = null, onEdit: (Int) -> Unit) {
    div(className = "form-control w-full flex flex-col gap-1") {
        label(className = "label") { span(className = "label-text font-medium") { +label } }
        div(className = "relative flex items-center") {
            numeric(value = value, min = 0, decimals = 0, className = "input input-bordered w-full pr-14") {
                attribute("step", "1")
                onInput { onEdit(this.value?.toInt() ?: 0) }
                onChange { onEdit(this.value?.toInt() ?: 0) }
            }
            if (suffix != null) span(className = "absolute right-4 text-base-content/50 text-sm") { +suffix }
        }
        if (hint != null) div(className = "text-xs text-base-content/60 mt-1 leading-snug") { +hint }
    }
}
