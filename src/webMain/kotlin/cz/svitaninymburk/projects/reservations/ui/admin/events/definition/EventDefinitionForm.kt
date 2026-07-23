package cz.svitaninymburk.projects.reservations.ui.admin.events.definition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.events.CustomFieldsBuilderSection
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.p
import dev.kilua.html.span
import web.history.history

@Composable
fun IComponent.AdminCreateEventDefinitionScreen(currentUser: User) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember(currentUser) { buildAdminCreateEventDefinitionModel(scope, router, currentUser) }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { history.back() }
            }
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.newTemplateTitle }
                p(className = "text-base-content/60") { +currentStrings.newTemplateSubtitle }
            }
        }

        EventDefinitionFieldsCard(
            title = model.title, onTitleChange = { model.title = it },
            description = model.description, onDescriptionChange = { model.description = it },
            ownerEmails = model.ownerEmails, onOwnerEmailsChange = { model.ownerEmails = it },
            price = model.price, onPriceChange = { model.price = it },
            capacity = model.capacity, onCapacityChange = { model.capacity = it },
            durationHours = model.durationHours, onDurationHoursChange = { model.durationHours = it },
            durationMinutes = model.durationMinutes, onDurationMinutesChange = { model.durationMinutes = it },
            allowBankTransfer = model.allowBankTransfer, onAllowBankTransferChange = { model.allowBankTransfer = it },
            allowOnSite = model.allowOnSite, onAllowOnSiteChange = { model.allowOnSite = it },
            showAttendeeCount = model.showAttendeeCount, onShowAttendeeCountChange = { model.showAttendeeCount = it },
        )

        CustomFieldsBuilderSection(model.customFields) { model.customFields = it }

        div(className = "flex justify-end gap-2 mt-4") {
            button(className = "btn") {
                onClick { history.back() }
                +currentStrings.cancel
            }
            button(className = "btn btn-primary") {
                disabled(model.isSubmitting)
                onClick { model.submit() }
                if (model.isSubmitting) span(className = "loading loading-spinner loading-sm")
                span(className = "icon-[heroicons--check] size-5")
                +currentStrings.createTemplateButton
            }
        }
    }

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Success,
        onDismiss = { model.dismissToast() },
    )
}
