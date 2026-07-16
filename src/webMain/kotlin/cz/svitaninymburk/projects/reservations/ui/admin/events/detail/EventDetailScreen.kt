package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.AddSeriesLessonRequest
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.UpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.AdminActionType
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.PendingAction
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.ReservationActionModal
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationModal
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.span
import dev.kilua.rpc.getService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

// UI Stavy
private sealed interface AdminEventDetailUiState {
    data object Loading : AdminEventDetailUiState
    data class Success(val data: AdminEventDetailData) : AdminEventDetailUiState
    data class Error(val message: String) : AdminEventDetailUiState
}

@Composable
fun IComponent.AdminEventDetailScreen(eventId: String, isSeries: Boolean) {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val reservationService = getService<ReservationServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings

    var refreshTrigger by remember { mutableStateOf(0) }
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    // Účastníci
    var expandedId by remember { mutableStateOf<Uuid?>(null) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var isModalLoading by remember { mutableStateOf(false) }

    // Zrušení / smazání akce
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleteLoading by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var isCancelLoading by remember { mutableStateOf(false) }
    var refundMoney by remember { mutableStateOf(true) }

    // Lekce kurzu
    var lessonsRefreshTrigger by remember { mutableStateOf(0) }
    var cancelLessonPending by remember { mutableStateOf<EventInstance?>(null) }
    var isCancelLessonLoading by remember { mutableStateOf(false) }
    var togglingDropInId by remember { mutableStateOf<Uuid?>(null) }
    var showAddLesson by remember { mutableStateOf(false) }
    var isAddingLesson by remember { mutableStateOf(false) }

    // Ruční rezervace administrátorem
    var reservationTarget by remember { mutableStateOf<ReservationTarget?>(null) }
    var isWaitlistSignup by remember { mutableStateOf(false) }
    var isSubmittingReservation by remember { mutableStateOf(false) }
    var isLoadingReservationTarget by remember { mutableStateOf(false) }

    // Načítání dat z backendu
    val uiState by produceState<AdminEventDetailUiState>(initialValue = AdminEventDetailUiState.Loading, key1 = refreshTrigger) {
        try {
            val uuid = Uuid.parse(eventId)
            adminService.getEventDetail(uuid, isSeries)
                .onRight { value = AdminEventDetailUiState.Success(it) }
                .onLeft { value = AdminEventDetailUiState.Error(it.localizedMessage(currentStrings)) }
        } catch (e: IllegalArgumentException) {
            value = AdminEventDetailUiState.Error(currentStrings.invalidEventId)
        }
    }

    val lessonsState by produceState<List<EventInstance>?>(
        initialValue = null,
        key1 = lessonsRefreshTrigger,
        key2 = eventId,
    ) {
        if (isSeries) {
            try {
                val uuid = Uuid.parse(eventId)
                adminService.getSeriesInstances(uuid, page = 0, pageSize = 200)
                    .onRight { value = it.items }
                    .onLeft { value = emptyList() }
            } catch (_: Exception) { value = emptyList() }
        }
    }

    fun loadReservationTarget(asWaitlist: Boolean) {
        isLoadingReservationTarget = true
        scope.launch {
            try {
                val uuid = Uuid.parse(eventId)
                val eventService = getService<EventServiceInterface>(RpcSerializersModules)
                if (isSeries) {
                    eventService.getSeriesDetail(uuid).onRight { detail ->
                        reservationTarget = ReservationTarget.Series(detail.series)
                        isWaitlistSignup = asWaitlist
                    }.onLeft { error ->
                        toastData = ToastData(error.localizedMessage(currentStrings), ToastType.Error)
                    }
                } else {
                    eventService.getInstance(uuid).onRight { instance ->
                        reservationTarget = ReservationTarget.Instance(instance)
                        isWaitlistSignup = asWaitlist
                    }.onLeft { error ->
                        toastData = ToastData(error.localizedMessage(currentStrings), ToastType.Error)
                    }
                }
            } catch (e: Exception) {
                toastData = ToastData(e.message ?: "Error", ToastType.Error)
            } finally {
                isLoadingReservationTarget = false
            }
        }
    }

    suspend fun submitManualReservation(target: ReservationTarget, formData: ReservationFormData) {
        isSubmittingReservation = true
        val result = when {
            formData.asWaitlist && target is ReservationTarget.Instance -> reservationService.joinWaitlistInstance(
                request = formData.toCreateInstanceReservationRequest(target.id),
                userId = null
            )
            formData.asWaitlist && target is ReservationTarget.Series -> reservationService.joinWaitlistSeries(
                request = formData.toCreateSeriesReservationRequest(target.id),
                userId = null
            )
            target is ReservationTarget.Instance -> reservationService.reserveInstance(
                request = formData.toCreateInstanceReservationRequest(target.id),
                userId = null
            )
            else -> reservationService.reserveSeries(
                request = formData.toCreateSeriesReservationRequest((target as ReservationTarget.Series).id),
                userId = null
            )
        }
        isSubmittingReservation = false
        result
            .onRight {
                toastData = ToastData(currentStrings.reservationCreated, ToastType.Success)
                reservationTarget = null
                refreshTrigger++
            }
            .onLeft { error ->
                toastData = ToastData(error.localizedMessage(currentStrings), ToastType.Error)
            }
    }

    fun confirmPendingAction(action: PendingAction) {
        isModalLoading = true
        scope.launch {
            when (action.type) {
                AdminActionType.CONFIRM_PAYMENT ->
                    adminService.markReservationAsPaid(action.reservationId)
                        .onRight {
                            toastData = ToastData(currentStrings.toastPaymentConfirmed(action.participantName), ToastType.Success)
                            refreshTrigger++
                        }
                        .onLeft { error ->
                            toastData = ToastData(currentStrings.errorToast(error.localizedMessage(currentStrings)), ToastType.Error)
                        }
                AdminActionType.CANCEL_RESERVATION ->
                    reservationService.cancelReservation(action.reservationId)
                        .onRight {
                            toastData = ToastData(currentStrings.toastReservationCancelled(action.participantName), ToastType.Success)
                            refreshTrigger++
                        }
                        .onLeft { error ->
                            toastData = ToastData(currentStrings.errorToast(error.localizedMessage(currentStrings)), ToastType.Error)
                        }
            }
            isModalLoading = false
            pendingAction = null
        }
    }

    fun deleteEvent() {
        isDeleteLoading = true
        scope.launch {
            val uuid = Uuid.parse(eventId)
            val result = if (isSeries)
                adminService.deleteEventSeries(uuid, refundMoney)
            else
                adminService.deleteEventInstance(uuid, refundMoney)
            result
                .onRight {
                    toastData = ToastData(
                        if (isSeries) currentStrings.toastSeriesDeleted else currentStrings.toastEventDeleted,
                        ToastType.Success
                    )
                    delay(500)
                    router.navigate("/admin/events")
                }
                .onLeft {
                    toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error)
                    isDeleteLoading = false
                }
        }
    }

    fun cancelEvent() {
        isCancelLoading = true
        scope.launch {
            val uuid = Uuid.parse(eventId)
            val result = if (isSeries)
                adminService.cancelEventSeries(uuid, refundMoney)
            else
                adminService.cancelEventInstance(uuid, refundMoney)
            result
                .onRight {
                    toastData = ToastData(currentStrings.cancelEventSuccess, ToastType.Success)
                    delay(500)
                    router.navigate("/admin/events")
                }
                .onLeft {
                    toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error)
                    isCancelLoading = false
                    showCancelConfirm = false
                }
        }
    }

    fun toggleLessonDropIn(lesson: EventInstance) {
        togglingDropInId = lesson.id
        scope.launch {
            adminService.updateEventInstance(
                lesson.id,
                UpdateEventInstanceRequest(
                    title = lesson.title,
                    description = lesson.description,
                    startDateTime = lesson.startDateTime,
                    endDateTime = lesson.endDateTime,
                    price = lesson.price,
                    capacity = lesson.capacity,
                    allowedPaymentTypes = lesson.allowedPaymentTypes,
                    customFields = lesson.customFields,
                    isDropIn = !lesson.isDropIn,
                )
            ).onLeft { error ->
                toastData = ToastData(currentStrings.errorToast(error.localizedMessage(currentStrings)), ToastType.Error)
            }
            lessonsRefreshTrigger++
            togglingDropInId = null
        }
    }

    fun cancelLesson(lesson: EventInstance) {
        isCancelLessonLoading = true
        scope.launch {
            adminService.cancelSeriesLesson(lesson.id)
                .onRight {
                    toastData = ToastData(currentStrings.toastLessonCancelled, ToastType.Success)
                    lessonsRefreshTrigger++
                    refreshTrigger++
                }
                .onLeft {
                    toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error)
                }
            isCancelLessonLoading = false
            cancelLessonPending = null
        }
    }

    fun addLesson(startDateTime: LocalDateTime, endDateTime: LocalDateTime, isDropIn: Boolean) {
        isAddingLesson = true
        scope.launch {
            try {
                val uuid = Uuid.parse(eventId)
                adminService.addSeriesLesson(
                    AddSeriesLessonRequest(
                        seriesId = uuid,
                        startDateTime = startDateTime,
                        endDateTime = endDateTime,
                        isDropIn = isDropIn,
                    )
                ).onRight {
                    showAddLesson = false
                    toastData = ToastData(currentStrings.toastLessonAdded, ToastType.Success)
                    lessonsRefreshTrigger++
                    refreshTrigger++
                }.onLeft { error ->
                    toastData = ToastData(currentStrings.errorToast(error.localizedMessage(currentStrings)), ToastType.Error)
                }
            } catch (e: Exception) {
                toastData = ToastData(e.message ?: "Error", ToastType.Error)
            } finally {
                isAddingLesson = false
            }
        }
    }

    when (val state = uiState) {
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
                    data = data,
                    eventId = eventId,
                    isSeries = isSeries,
                    onCancelEvent = { refundMoney = true; showCancelConfirm = true },
                    onDeleteEvent = { refundMoney = true; showDeleteConfirm = true },
                )

                EventDetailStats(data)

                if (isSeries) {
                    SeriesLessonsCard(
                        lessons = lessonsState,
                        togglingDropInId = togglingDropInId,
                        onAddLesson = { showAddLesson = true },
                        onToggleDropIn = { toggleLessonDropIn(it) },
                        onCancelLesson = { cancelLessonPending = it },
                    )
                }

                ParticipantsCard(
                    data = data,
                    expandedId = expandedId,
                    onExpandedChange = { expandedId = it },
                    isLoadingReservationTarget = isLoadingReservationTarget,
                    isWaitlistSignup = isWaitlistSignup,
                    onAddReservation = { asWaitlist -> loadReservationTarget(asWaitlist) },
                    onConfirmPayment = {
                        pendingAction = PendingAction(AdminActionType.CONFIRM_PAYMENT, it.reservationId, it.contactName)
                    },
                    onCancelReservation = {
                        pendingAction = PendingAction(AdminActionType.CANCEL_RESERVATION, it.reservationId, it.contactName)
                    },
                )

                if (data.waitlistCapacity > 0) {
                    WaitlistCard(
                        data = data,
                        onCancelReservation = {
                            pendingAction = PendingAction(AdminActionType.CANCEL_RESERVATION, it.reservationId, it.contactName)
                        },
                    )
                }
            }
        }
    }

    pendingAction?.let { action ->
        ReservationActionModal(
            action = action,
            isLoading = isModalLoading,
            onConfirm = { confirmPendingAction(action) },
            onDismiss = { pendingAction = null },
        )
    }

    if (showDeleteConfirm) {
        DeleteEventModal(
            isSeries = isSeries,
            reservationCount = (uiState as? AdminEventDetailUiState.Success)?.data?.participants?.size ?: 0,
            refundMoney = refundMoney,
            onRefundMoneyChange = { refundMoney = it },
            isLoading = isDeleteLoading,
            onConfirm = { deleteEvent() },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showCancelConfirm) {
        CancelEventModal(
            reservationCount = (uiState as? AdminEventDetailUiState.Success)?.data?.participants?.size ?: 0,
            refundMoney = refundMoney,
            onRefundMoneyChange = { refundMoney = it },
            isLoading = isCancelLoading,
            onConfirm = { cancelEvent() },
            onDismiss = { showCancelConfirm = false },
        )
    }

    cancelLessonPending?.let { lesson ->
        CancelLessonModal(
            lesson = lesson,
            isLoading = isCancelLessonLoading,
            onConfirm = { cancelLesson(lesson) },
            onDismiss = { cancelLessonPending = null },
        )
    }

    if (showAddLesson) {
        AddLessonModal(
            lastLesson = lessonsState?.maxByOrNull { it.startDateTime },
            inheritedCustomFields = (uiState as? AdminEventDetailUiState.Success)?.data?.customFields ?: emptyList(),
            isSubmitting = isAddingLesson,
            onSubmit = { start, end, isDropIn -> addLesson(start, end, isDropIn) },
            onInvalidInput = { toastData = ToastData(currentStrings.addLessonInvalidDateTime, ToastType.Error) },
            onDismiss = { showAddLesson = false },
        )
    }

    ReservationModal(
        target = reservationTarget,
        user = null,
        isSubmitting = isSubmittingReservation,
        asWaitlist = isWaitlistSignup,
        onClose = { reservationTarget = null; isWaitlistSignup = false },
        onSubmit = { target, formData -> scope.launch { submitManualReservation(target, formData) } }
    )

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}
