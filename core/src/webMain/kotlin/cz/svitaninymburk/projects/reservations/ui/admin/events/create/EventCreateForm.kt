package cz.svitaninymburk.projects.reservations.ui.admin.events.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.ui.admin.events.CustomFieldsBuilderSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.ReservationDeadlineSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.EventCreateType
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.p

@Composable
fun IComponent.AdminCreateEventScreen(currentUser: User) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val model = remember { buildAdminCreateEventModel(scope, router, currentUser.email) }
    val currentStrings by strings

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

        EventCreateHeader()

        EventTypeSelectorCard(model.eventType) { model.onEventTypeChange(it) }

        EventBasicInfoCard(model)

        when (model.eventType) {
            EventCreateType.SINGLE -> SingleEventScheduleCard(model)
            EventCreateType.RECURRING -> RecurringEventScheduleCard(model)
            EventCreateType.COURSE -> CourseScheduleCard(model)
        }

        CustomFieldsBuilderSection(model.customFields) { model.customFields = it }

        ReservationDeadlineSection(model.deadline)

        EventCreateActions(model)
    }

    model.pendingPastStart?.let { pending ->
        ConfirmModal(
            title = currentStrings.pastStartConfirmTitle,
            confirmLabel = currentStrings.pastStartConfirmButton,
            dismissLabel = currentStrings.modalBack,
            isLoading = false,
            onConfirm = { model.confirmPastStart() },
            onDismiss = { model.dismissPastStart() },
            confirmClassName = "btn-warning",
        ) { p(className = "py-4") { +currentStrings.pastStartConfirmMessage(pending.firstStartLabel) } }
    }

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Success,
        onDismiss = { model.dismissToast() },
    )
}
