package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.admin.AdminParticipantRow
import cz.svitaninymburk.projects.reservations.admin.AuditLogPage
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.AdminReservationUseCase
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.EventAuditLogQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.EventDetailQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.EventLifecycleUseCase
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.SeriesLessonsUseCase
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.AdminActionType
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.PendingAction
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

sealed interface AdminEventDetailUiState {
    data object Loading : AdminEventDetailUiState
    data class Success(val data: AdminEventDetailData) : AdminEventDetailUiState
    data class Error(val message: String) : AdminEventDetailUiState
}

class AdminEventDetailModel(
    scope: CoroutineScope,
    private val queries: EventDetailQueries,
    private val lifecycle: EventLifecycleUseCase,
    private val lessons: SeriesLessonsUseCase,
    private val reservations: AdminReservationUseCase,
    private val auditLog: EventAuditLogQueries,
    private val router: Router,
    private val eventId: String,
    private val isSeries: Boolean,
) : ScreenModel(scope) {

    var uiState: AdminEventDetailUiState by mutableStateOf(AdminEventDetailUiState.Loading); private set
    var lessonsState: List<EventInstance>? by mutableStateOf(null); private set

    // cancel / delete
    var showDeleteConfirm by mutableStateOf(false); private set
    var isDeleteLoading by mutableStateOf(false); private set
    var showCancelConfirm by mutableStateOf(false); private set
    var isCancelLoading by mutableStateOf(false); private set
    var refundMoney by mutableStateOf(true)

    // participant actions
    var pendingAction: PendingAction? by mutableStateOf(null); private set
    var isModalLoading by mutableStateOf(false); private set

    // lessons
    var cancelLessonPending: EventInstance? by mutableStateOf(null)
    var isCancelLessonLoading by mutableStateOf(false); private set
    var togglingDropInId: Uuid? by mutableStateOf(null); private set
    var showAddLesson by mutableStateOf(false)
    var isAddingLesson by mutableStateOf(false); private set

    // manual reservation
    var reservationTarget: ReservationTarget? by mutableStateOf(null)
    var isWaitlistSignup by mutableStateOf(false); private set
    var isSubmittingReservation by mutableStateOf(false); private set
    var isLoadingReservationTarget by mutableStateOf(false); private set

    // Historie — schválně mimo uiState: refresh() po každé akci s účastníkem
    // přenačítá detail a nemá přitom shodit stránkování ani filtr historie.
    var auditPageData: AuditLogPage? by mutableStateOf(null); private set
    var auditError: String? by mutableStateOf(null); private set
    var auditPage by mutableIntStateOf(0); private set
    var auditCategory: AuditCategory? by mutableStateOf(null); private set
    var isAuditLoading by mutableStateOf(false); private set
    /** Načítá se až po rozbalení, ať se nezdržuje první vykreslení detailu. */
    var isAuditExpanded by mutableStateOf(false); private set

    private val uuid: Uuid get() = Uuid.parse(eventId)

    fun load() {
        val parsed = runCatching { Uuid.parse(eventId) }.getOrNull()
            ?: run { uiState = AdminEventDetailUiState.Error(currentStrings.invalidEventId); return }
        scope.launch {
            queries.detail(parsed, isSeries)
                .onRight { uiState = AdminEventDetailUiState.Success(it) }
                .onLeft { uiState = AdminEventDetailUiState.Error(it.localizedMessage(currentStrings)) }
        }
        if (isSeries) scope.launch {
            lessons.instances(parsed).onRight { lessonsState = it.items }.onLeft { lessonsState = emptyList() }
        }
    }

    private fun loadLessons(id: Uuid) {
        run(
            errorMessage = { it.localizedMessage(currentStrings) },
            block = { lessons.instances(id) },
            onSuccess = { lessonsState = it.items },
        )
    }

    /**
     * Po akci s účastníkem se přenačte i historie — jinak by v ní admin neviděl
     * to, co právě udělal. Stránka ani filtr se přitom neresetují.
     */
    fun refresh() {
        load()
        if (isAuditExpanded) loadAudit()
    }

    fun toggleAuditExpanded() {
        isAuditExpanded = !isAuditExpanded
        if (isAuditExpanded && auditPageData == null) loadAudit()
    }

    fun goToAuditPage(page: Int) {
        auditPage = page
        loadAudit()
    }

    fun setAuditCategory(category: AuditCategory?) {
        auditCategory = category
        auditPage = 0
        loadAudit()
    }

    private fun loadAudit() {
        val parsed = runCatching { Uuid.parse(eventId) }.getOrNull() ?: return
        isAuditLoading = true
        auditError = null
        scope.launch {
            auditLog.page(parsed, isSeries, auditPage, auditCategory)
                .onRight { auditPageData = it }
                .onLeft { auditError = it.localizedMessage(currentStrings) }
            isAuditLoading = false
        }
    }

    fun requestDelete() { refundMoney = true; showDeleteConfirm = true }
    fun requestCancel() { refundMoney = true; showCancelConfirm = true }
    fun dismissDelete() { showDeleteConfirm = false }
    fun dismissCancel() { showCancelConfirm = false }

    fun deleteEvent() = run(
        loading = { isDeleteLoading = it },
        errorMessage = { it.localizedMessage(currentStrings) },
        block = { lifecycle.delete(uuid, isSeries, refundMoney) },
        onSuccess = {
            showToast(if (isSeries) currentStrings.toastSeriesDeleted else currentStrings.toastEventDeleted)
            scope.launch { delay(500); router.navigate("/admin/events") }
        },
    )

    fun cancelEvent() = run(
        loading = { isCancelLoading = it },
        errorMessage = { it.localizedMessage(currentStrings) },
        block = { lifecycle.cancel(uuid, isSeries, refundMoney) },
        onSuccess = {
            showCancelConfirm = false
            showToast(currentStrings.cancelEventSuccess)
            scope.launch { delay(500); router.navigate("/admin/events") }
        },
    )

    fun addLesson(start: LocalDateTime, end: LocalDateTime, dropIn: Boolean) = run(
        loading = { isAddingLesson = it },
        errorMessage = { it.localizedMessage(currentStrings) },
        block = { lessons.add(uuid, start, end, dropIn) },
        onSuccess = { showAddLesson = false; showToast(currentStrings.toastLessonAdded); refresh() },
    )

    fun cancelLesson(lesson: EventInstance) = run(
        loading = { isCancelLessonLoading = it },
        errorMessage = { it.localizedMessage(currentStrings) },
        block = { lessons.cancel(lesson.id) },
        onSuccess = { cancelLessonPending = null; showToast(currentStrings.toastLessonCancelled); refresh() },
    )

    fun toggleDropIn(lesson: EventInstance) {
        togglingDropInId = lesson.id
        run(
            errorMessage = { it.localizedMessage(currentStrings) },
            block = { lessons.toggleDropIn(lesson) },
            onSuccess = { },
        ).invokeOnCompletion { togglingDropInId = null; if (isSeries) loadLessons(uuid) }
    }

    fun loadReservationTarget(asWaitlist: Boolean) = run(
        loading = { isLoadingReservationTarget = it },
        errorMessage = { it.localizedMessage(currentStrings) },
        block = { reservations.target(uuid, isSeries) },
        onSuccess = { reservationTarget = it; isWaitlistSignup = asWaitlist },
    )

    fun submitReservation(target: ReservationTarget, form: ReservationFormData) = run(
        loading = { isSubmittingReservation = it },
        errorMessage = { it.localizedMessage(currentStrings) },
        block = { reservations.submit(target, form) },
        onSuccess = { showToast(currentStrings.reservationCreated); reservationTarget = null; isWaitlistSignup = false; refresh() },
    )

    fun dismissReservation() { reservationTarget = null; isWaitlistSignup = false }

    fun invalidAddLessonInput() = showToast(currentStrings.addLessonInvalidDateTime, ToastType.Error)

    fun confirmPayment(row: AdminParticipantRow) {
        pendingAction = PendingAction(AdminActionType.CONFIRM_PAYMENT, row.reservationId, row.contactName)
    }

    fun cancelReservation(row: AdminParticipantRow) {
        pendingAction = PendingAction(AdminActionType.CANCEL_RESERVATION, row.reservationId, row.contactName)
    }

    fun dismissPendingAction() { pendingAction = null }

    fun confirmPendingAction(action: PendingAction) {
        when (action.type) {
            AdminActionType.CONFIRM_PAYMENT -> run(
                loading = { isModalLoading = it },
                errorMessage = { it.localizedMessage(currentStrings) },
                block = { reservations.confirmPayment(action.reservationId) },
                onSuccess = { showToast(currentStrings.toastPaymentConfirmed(action.participantName)); refresh() },
            ).invokeOnCompletion { pendingAction = null }
            AdminActionType.CANCEL_RESERVATION -> run(
                loading = { isModalLoading = it },
                errorMessage = { it.localizedMessage(currentStrings) },
                block = { reservations.cancel(action.reservationId) },
                onSuccess = { showToast(currentStrings.toastReservationCancelled(action.participantName)); refresh() },
            ).invokeOnCompletion { pendingAction = null }
        }
    }
}

fun IComponent.buildAdminEventDetailModel(
    scope: CoroutineScope,
    router: Router,
    eventId: String,
    isSeries: Boolean,
): AdminEventDetailModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    val reservation = getService<ReservationServiceInterface>(RpcSerializersModules)
    val event = getService<EventServiceInterface>(RpcSerializersModules)
    return AdminEventDetailModel(
        scope = scope,
        queries = EventDetailQueries(admin),
        lifecycle = EventLifecycleUseCase(admin),
        lessons = SeriesLessonsUseCase(admin),
        reservations = AdminReservationUseCase(admin, reservation, event),
        auditLog = EventAuditLogQueries(admin),
        router = router,
        eventId = eventId,
        isSeries = isSeries,
    )
}
