package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.ReservationActionModal
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationModal
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.span

@Composable
fun IComponent.AdminEventDetailScreen(eventId: String, isSeries: Boolean) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember(eventId, isSeries) { buildAdminEventDetailModel(scope, router, eventId, isSeries) }

    LaunchedEffect(eventId, isSeries) { model.load() }

    when (val state = model.uiState) {
        is AdminEventDetailUiState.Loading -> Loading()
        is AdminEventDetailUiState.Error -> {
            div(className = "alert alert-error max-w-lg mx-auto mt-10") {
                span(className = "icon-[heroicons--x-circle] size-6")
                span { +state.message }
                button(className = "btn btn-sm") {
                    onClick { router.navigate("/admin") }
                    +currentStrings.backToDashboard
                }
            }
        }
        is AdminEventDetailUiState.Success -> {
            val data = state.data
            div(className = "flex flex-col gap-6 animate-fade-in") {
                EventDetailHeader(
                    data = data, eventId = eventId, isSeries = isSeries,
                    onCancelEvent = { model.requestCancel() },
                    onDeleteEvent = { model.requestDelete() },
                )
                EventDetailStats(data)
                if (isSeries) {
                    SeriesLessonsCard(
                        lessons = model.lessonsState,
                        togglingDropInId = model.togglingDropInId,
                        onAddLesson = { model.showAddLesson = true },
                        onToggleDropIn = { model.toggleDropIn(it) },
                        onCancelLesson = { model.cancelLessonPending = it },
                    )
                }
                ParticipantsCard(
                    data = data,
                    isLoadingReservationTarget = model.isLoadingReservationTarget,
                    isWaitlistSignup = model.isWaitlistSignup,
                    onAddReservation = { model.loadReservationTarget(it) },
                    onConfirmPayment = { model.confirmPayment(it) },
                    onCancelReservation = { model.cancelReservation(it) },
                )
                AuditLogCard(
                    page = model.auditPageData,
                    errorMessage = model.auditError,
                    isExpanded = model.isAuditExpanded,
                    isLoading = model.isAuditLoading,
                    currentPage = model.auditPage,
                    category = model.auditCategory,
                    onToggle = { model.toggleAuditExpanded() },
                    onPageChange = { model.goToAuditPage(it) },
                    onCategoryChange = { model.setAuditCategory(it) },
                )
                if (data.waitlistCapacity > 0) {
                    WaitlistCard(data = data, onCancelReservation = { model.cancelReservation(it) })
                }
            }
        }
    }

    model.pendingAction?.let { action ->
        ReservationActionModal(
            action = action, isLoading = model.isModalLoading,
            onConfirm = { model.confirmPendingAction(action) },
            onDismiss = { model.dismissPendingAction() },
        )
    }
    if (model.showDeleteConfirm) {
        DeleteEventModal(
            isSeries = isSeries,
            reservationCount = (model.uiState as? AdminEventDetailUiState.Success)?.data?.participants?.size ?: 0,
            refundMoney = model.refundMoney, onRefundMoneyChange = { model.refundMoney = it },
            isLoading = model.isDeleteLoading,
            onConfirm = { model.deleteEvent() }, onDismiss = { model.dismissDelete() },
        )
    }
    if (model.showCancelConfirm) {
        CancelEventModal(
            reservationCount = (model.uiState as? AdminEventDetailUiState.Success)?.data?.participants?.size ?: 0,
            refundMoney = model.refundMoney, onRefundMoneyChange = { model.refundMoney = it },
            isLoading = model.isCancelLoading,
            onConfirm = { model.cancelEvent() }, onDismiss = { model.dismissCancel() },
        )
    }
    model.cancelLessonPending?.let { lesson ->
        CancelLessonModal(
            lesson = lesson, isLoading = model.isCancelLessonLoading,
            onConfirm = { model.cancelLesson(lesson) }, onDismiss = { model.cancelLessonPending = null },
        )
    }
    if (model.showAddLesson) {
        AddLessonModal(
            lastLesson = model.lessonsState?.maxByOrNull { it.startDateTime },
            inheritedCustomFields = (model.uiState as? AdminEventDetailUiState.Success)?.data?.customFields ?: emptyList(),
            isSubmitting = model.isAddingLesson,
            onSubmit = { start, end, dropIn -> model.addLesson(start, end, dropIn) },
            onInvalidInput = { model.invalidAddLessonInput() },
            onDismiss = { model.showAddLesson = false },
        )
    }
    ReservationModal(
        target = model.reservationTarget, user = null,
        isSubmitting = model.isSubmittingReservation, asWaitlist = model.isWaitlistSignup,
        onClose = { model.dismissReservation() },
        onSubmit = { target, form -> model.submitReservation(target, form) },
    )
    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Success,
        onDismiss = { model.dismissToast() },
    )
}
