package cz.svitaninymburk.projects.reservations.ui.admin.events.definition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowedPaymentsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.DurationField
import cz.svitaninymburk.projects.reservations.ui.admin.events.OwnerEmailsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.PriceCurrencyField
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowMultipleSeatsCheckbox
import cz.svitaninymburk.projects.reservations.ui.admin.events.ShowAttendeeCountCheckbox
import dev.kilua.core.IComponent
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.div
import dev.kilua.html.h2
import dev.kilua.html.label
import dev.kilua.html.span

@Composable
fun IComponent.EventDefinitionFieldsCard(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    ownerEmails: List<String>,
    onOwnerEmailsChange: (List<String>) -> Unit,
    price: Number?,
    onPriceChange: (Number?) -> Unit,
    capacity: Int,
    onCapacityChange: (Int) -> Unit,
    durationHours: Int,
    onDurationHoursChange: (Int) -> Unit,
    durationMinutes: Int,
    onDurationMinutesChange: (Int) -> Unit,
    allowBankTransfer: Boolean,
    onAllowBankTransferChange: (Boolean) -> Unit,
    allowOnSite: Boolean,
    onAllowOnSiteChange: (Boolean) -> Unit,
    showAttendeeCount: Boolean,
    onShowAttendeeCountChange: (Boolean) -> Unit,
    allowMultipleSeats: Boolean,
    onAllowMultipleSeatsChange: (Boolean) -> Unit,
) {
    val currentStrings by strings
    div(className = "card bg-base-100 shadow-sm") {
        div(className = "card-body") {
            h2(className = "card-title text-lg mb-4") { +currentStrings.basicInfoHeading }
            div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                div(className = "form-control w-full md:col-span-2") {
                    label(className = "label") { span(className = "label-text font-medium") { +currentStrings.eventNameLabel } }
                    text(value = title, className = "input input-bordered w-full") { onInput { onTitleChange(value ?: "") } }
                }
                div(className = "form-control w-full md:col-span-2") {
                    label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                    textArea(value = description, className = "textarea textarea-bordered h-24 w-full") { onInput { onDescriptionChange(value ?: "") } }
                }
                OwnerEmailsField(ownerEmails, onOwnerEmailsChange)
                PriceCurrencyField(currentStrings.defaultPriceLabel, price, onChange = onPriceChange)
                CapacityField(capacity, onCapacityChange)
                DurationField(currentStrings.defaultDurationLabel, durationHours, durationMinutes, onDurationHoursChange, onDurationMinutesChange)
                AllowedPaymentsField(allowBankTransfer, allowOnSite, onAllowBankTransferChange, onAllowOnSiteChange)
                ShowAttendeeCountCheckbox(showAttendeeCount, onShowAttendeeCountChange)
                AllowMultipleSeatsCheckbox(allowMultipleSeats, onAllowMultipleSeatsChange)
            }
        }
    }
}
