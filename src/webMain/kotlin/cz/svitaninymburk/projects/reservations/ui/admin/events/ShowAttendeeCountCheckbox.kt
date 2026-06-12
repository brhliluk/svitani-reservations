package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.form.check.checkBox
import dev.kilua.html.*

@Composable
fun IComponent.ShowAttendeeCountCheckbox(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    val currentStrings by strings
    div(className = "form-control w-full md:col-span-2") {
        p(className = "label-text font-medium mb-1") { +currentStrings.showAttendeeCount }
        label(className = "cursor-pointer label justify-start gap-3") {
            checkBox(value = value, className = "checkbox checkbox-primary") {
                onChange { onValueChange(value) }
            }
            span(className = "label-text text-sm text-base-content/70") { +currentStrings.showAttendeeCountHint }
        }
    }
}
