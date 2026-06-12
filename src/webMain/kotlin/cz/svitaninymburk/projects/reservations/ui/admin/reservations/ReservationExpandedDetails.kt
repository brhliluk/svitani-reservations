package cz.svitaninymburk.projects.reservations.ui.admin.reservations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.reservation.CustomFieldsDisplay
import cz.svitaninymburk.projects.reservations.util.PhoneNumber
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.span

@Composable
fun IComponent.ReservationExpandedDetails(
    phone: String?,
    createdAtText: String,
    customFields: List<CustomFieldDefinition>,
    customValues: Map<String, CustomFieldValue>,
) {
    val currentStrings by strings

    div(className = "grid grid-cols-1 md:grid-cols-2 gap-6") {
        div(className = "flex flex-col gap-2") {
            if (!phone.isNullOrBlank()) {
                div(className = "flex justify-between items-baseline gap-4") {
                    span(className = "text-xs uppercase font-bold tracking-wider text-base-content/60") { +currentStrings.phoneLabel }
                    span(className = "font-medium text-base-content text-sm") { +PhoneNumber.format(phone) }
                }
            }
            div(className = "flex justify-between items-baseline gap-4") {
                span(className = "text-xs uppercase font-bold tracking-wider text-base-content/60") { +currentStrings.createdAt }
                span(className = "font-medium text-base-content text-sm") { +createdAtText }
            }
        }

        if (customFields.isNotEmpty()) {
            div {
                div(className = "text-xs uppercase font-bold tracking-wider text-base-content/60 mb-2") {
                    +currentStrings.customFieldsHeading
                }
                CustomFieldsDisplay(customFields, customValues)
            }
        }
    }
}
