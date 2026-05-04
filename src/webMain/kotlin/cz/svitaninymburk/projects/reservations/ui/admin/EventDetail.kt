package cz.svitaninymburk.projects.reservations.ui.admin

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
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.UpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.form.form
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import web.history.history
import kotlin.uuid.Uuid

enum class AdminActionType { CONFIRM_PAYMENT, CANCEL_RESERVATION }
data class PendingAction(val type: AdminActionType, val reservationId: Uuid, val participantName: String)

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
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<Uuid?>(null) }

    var lessonsRefreshTrigger by remember { mutableStateOf(0) }
    val lessonsState by produceState<List<EventInstance>?>(
        initialValue = null,
        key1 = lessonsRefreshTrigger,
        key2 = eventId,
    ) {
        if (isSeries) {
            try {
                val uuid = Uuid.parse(eventId)
                adminService.getSeriesInstances(uuid)
                    .onRight { value = it }
                    .onLeft { value = emptyList() }
            } catch (_: Exception) { value = emptyList() }
        }
    }
    var cancelLessonPending by remember { mutableStateOf<EventInstance?>(null) }

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

                // --- 1. HLAVIČKA A TLAČÍTKO ZPĚT ---
                div(className = "flex items-center gap-4") {
                    button(className = "btn btn-circle btn-ghost btn-sm") {
                        span(className = "icon-[heroicons--arrow-left] size-5")
                        onClick { history.back() }
                    }
                    div(className = "flex-1") {
                        h1(className = "text-2xl font-bold text-base-content flex items-center gap-2") {
                            if (isSeries) span(className = "icon-[heroicons--academic-cap] text-secondary size-6")
                            else span(className = "icon-[heroicons--calendar] text-primary size-6")

                            +data.title
                        }
                        p(className = "text-base-content/60 text-sm") { +data.subtitle }
                    }
                    val navPrefix = if (isSeries) "/admin/events/series" else "/admin/events/instance"
                    button(className = "btn btn-outline btn-sm gap-2") {
                        span(className = "icon-[heroicons--clipboard-document-check] size-4")
                        +currentStrings.attendanceButton
                        onClick { router.navigate("$navPrefix/$eventId/attendance") }
                    }
                    val editPath = if (isSeries) "/admin/events/series/$eventId/edit" else "/admin/events/instance/$eventId/edit"
                    button(className = "btn btn-outline btn-sm gap-2") {
                        span(className = "icon-[heroicons--pencil] size-4")
                        if (isSeries) +currentStrings.editSeries else +currentStrings.editEvent
                        onClick { router.navigate(editPath) }
                    }
                    button(className = "btn btn-outline btn-error btn-sm gap-2") {
                        span(className = "icon-[heroicons--trash] size-4")
                        if (isSeries) +currentStrings.deleteSeriesLabel else +currentStrings.deleteEventLabel
                        onClick { showDeleteConfirm = true }
                    }
                }

                // --- 2. STATISTIKY ---
                div(className = "stats shadow-sm bg-base-100 w-full") {
                    div(className = "stat") {
                        div(className = "stat-title") { +currentStrings.occupancyStatTitle }
                        val isFull = data.occupiedSpots >= data.capacity
                        div(className = "stat-value ${if (isFull) "text-error" else "text-primary"}") {
                            +"${data.occupiedSpots} / ${data.capacity}"
                        }
                        div(className = "stat-desc") {
                            if (isFull) +currentStrings.capacityFilled else +currentStrings.spotsRemaining(data.capacity - data.occupiedSpots)
                        }
                    }
                    div(className = "stat") {
                        div(className = "stat-title") { +currentStrings.revenueStatTitle }
                        div(className = "stat-value text-success") { +"${data.totalCollected} ${currentStrings.currency}" }
                        div(className = "stat-desc") { +currentStrings.revenueStatDesc }
                    }
                }

                // --- 3. LEKCE KURZU ---
                if (isSeries) {
                    val lessons = lessonsState
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body") {
                            h2(className = "card-title text-lg mb-4") {
                                span(className = "icon-[heroicons--calendar-days] size-5 text-secondary")
                                +currentStrings.seriesLessonsHeading
                            }
                            if (lessons == null) {
                                div(className = "flex justify-center py-4") {
                                    span(className = "loading loading-spinner loading-md")
                                }
                            } else if (lessons.isEmpty()) {
                                p(className = "text-base-content/50 italic text-sm") { +currentStrings.noLessonsYet }
                            } else {
                                div(className = "overflow-x-auto") {
                                    table(className = "table table-sm w-full") {
                                        thead {
                                            tr {
                                                th { +currentStrings.tableHeaderDate }
                                                th { +currentStrings.tableHeaderTime }
                                                th { +currentStrings.status }
                                                th { +currentStrings.lessonIndividualLabel }
                                                th(className = "text-right") { +currentStrings.tableHeaderActions }
                                            }
                                        }
                                        tbody {
                                            lessons.forEach { lesson ->
                                                tr {
                                                    td(className = "font-medium") { +lesson.startDateTime.date.humanReadable }
                                                    td { +"${lesson.startDateTime.hour}:${lesson.startDateTime.minute.toString().padStart(2, '0')} – ${lesson.endDateTime.hour}:${lesson.endDateTime.minute.toString().padStart(2, '0')}" }
                                                    td {
                                                        if (lesson.isCancelled) {
                                                            div(className = "badge badge-error badge-sm") { +currentStrings.lessonCancelledBadge }
                                                        } else {
                                                            div(className = "badge badge-success badge-sm") { +currentStrings.lessonActiveBadge }
                                                        }
                                                    }
                                                    td {
                                                        if (!lesson.isCancelled) {
                                                            button(className = "btn btn-xs ${if (lesson.isDropIn) "btn-secondary" else "btn-ghost"}") {
                                                                span(className = "icon-[heroicons--globe-alt] size-3")
                                                                if (lesson.isDropIn) +" On" else +" Off"
                                                                onClick {
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
                                                                        )
                                                                        lessonsRefreshTrigger++
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    td(className = "text-right") {
                                                        div(className = "flex justify-end gap-1") {
                                                            if (!lesson.isCancelled) {
                                                                button(className = "btn btn-ghost btn-xs") {
                                                                    span(className = "icon-[heroicons--pencil] size-4")
                                                                    onClick { router.navigate("/admin/events/instance/${lesson.id}/edit") }
                                                                }
                                                                button(className = "btn btn-ghost btn-xs text-error") {
                                                                    span(className = "icon-[heroicons--x-circle] size-4")
                                                                    onClick { cancelLessonPending = lesson }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 4. TABULKA ÚČASTNÍKŮ ---
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body p-0") {
                        div(className = "overflow-x-auto") {
                            table(className = "table table-zebra w-full") {
                                thead {
                                    tr {
                                        th(className = "w-10") { }
                                        th { +currentStrings.tableHeaderParticipant }
                                        th { +currentStrings.tableHeaderSeats }
                                        th { +currentStrings.priceLabel }
                                        th { +currentStrings.tableHeaderPaymentStatus }
                                        th(className = "text-right") { +currentStrings.tableHeaderActions }
                                    }
                                }
                                tbody {
                                    if (data.participants.isEmpty()) {
                                        tr {
                                            td {
                                                attribute("colspan", "6")
                                                div(className = "text-center text-base-content/50 py-4 italic") {
                                                    +currentStrings.noParticipants
                                                }
                                            }
                                        }
                                    } else {
                                        data.participants.forEach { participant ->

                                            // Určení vzhledu řádku podle stavu a typu platby
                                            val isPaid = participant.status == Reservation.Status.CONFIRMED // Přizpůsob svému stavu
                                            val isCash = participant.paymentType == PaymentInfo.Type.ON_SITE // Přizpůsob svému enumu
                                            val isExpanded = expandedId == participant.reservationId

                                            tr(className = if (!isPaid && isCash) "bg-info/5" else "") {
                                                td {
                                                    button(className = "btn btn-ghost btn-xs tooltip tooltip-right") {
                                                        attribute("data-tip", if (isExpanded) currentStrings.hideDetails else currentStrings.showDetails)
                                                        onClick { expandedId = if (isExpanded) null else participant.reservationId }
                                                        span(className = "size-5 " + if (isExpanded) "icon-[heroicons--chevron-down]" else "icon-[heroicons--chevron-right]")
                                                    }
                                                }
                                                td {
                                                    div(className = "font-bold") { +participant.contactName }
                                                    div(className = "text-xs text-base-content/50") {
                                                        +"${participant.contactEmail} • ${participant.contactPhone}"
                                                    }
                                                }
                                                td { +"${participant.seatCount}" }
                                                td(className = if (!isPaid && isCash) "font-bold text-info" else "") {
                                                    +"${participant.totalPrice} ${currentStrings.currency}"
                                                }
                                                td {
                                                    div(className = "flex flex-col gap-1 items-start") {
                                                        if (isPaid) {
                                                            div(className = "badge badge-success gap-1") {
                                                                span(className = "icon-[heroicons--check] size-3")
                                                                +currentStrings.paid
                                                            }
                                                        } else if (isCash) {
                                                            div(className = "badge badge-info badge-outline gap-1") {
                                                                span(className = "icon-[heroicons--banknotes] size-3")
                                                                +currentStrings.statusOnSiteBadge
                                                            }
                                                        } else {
                                                            div(className = "badge badge-warning gap-1") {
                                                                span(className = "icon-[heroicons--clock] size-3")
                                                                +currentStrings.statusWaiting
                                                            }
                                                        }

                                                        span(className = "text-xs text-base-content/60 font-medium") {
                                                            if (isCash) +currentStrings.paymentMethodCash else +currentStrings.bankTransfer
                                                        }
                                                    }
                                                }
                                                td(className = "text-right") {
                                                    div(className = "flex justify-end gap-1") {

                                                        if (!isPaid) {
                                                            button(className = "btn btn-xs tooltip tooltip-left ${if (isCash) "btn-outline btn-info" else "btn-ghost text-success"}") {
                                                                attribute("data-tip", if (isCash) currentStrings.tooltipAcceptCash else currentStrings.tooltipMarkPaid)

                                                                onClick {
                                                                    pendingAction = PendingAction(
                                                                        type = AdminActionType.CONFIRM_PAYMENT,
                                                                        reservationId = participant.reservationId,
                                                                        participantName = participant.contactName
                                                                    )
                                                                }

                                                                span(className = "icon-[heroicons--check-circle] size-5")
                                                                if (isCash) +currentStrings.buttonCollect
                                                            }
                                                        }

                                                        button(className = "btn btn-ghost btn-xs text-error tooltip tooltip-left") {
                                                            attribute("data-tip", currentStrings.tooltipCancelReservation)

                                                            onClick {
                                                                pendingAction = PendingAction(
                                                                    type = AdminActionType.CANCEL_RESERVATION,
                                                                    reservationId = participant.reservationId,
                                                                    participantName = participant.contactName
                                                                )
                                                            }

                                                            span(className = "icon-[heroicons--trash] size-5")
                                                        }
                                                    }
                                                }
                                            }

                                            if (isExpanded) {
                                                tr(className = "bg-base-200/40") {
                                                    td {
                                                        attribute("colspan", "6")
                                                        div(className = "p-4") {
                                                            ReservationExpandedDetails(
                                                                phone = participant.contactPhone,
                                                                createdAtText = participant.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).humanReadable,
                                                                customFields = data.customFields,
                                                                customValues = participant.customValues,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingAction != null) {
        val action = pendingAction!!

        div(className = "modal modal-open") {
            div(className = "modal-box") {

                h3(className = "font-bold text-lg") {
                    if (action.type == AdminActionType.CONFIRM_PAYMENT) +currentStrings.modalConfirmPaymentTitle
                    else +currentStrings.modalCancelReservationTitle
                }

                p(className = "py-4") {
                    if (action.type == AdminActionType.CONFIRM_PAYMENT) {
                        +currentStrings.modalConfirmPaymentMsgPre
                        strong { +action.participantName }
                        +currentStrings.modalConfirmPaymentMsgPost
                    } else {
                        +currentStrings.modalCancelMsgPre
                        strong { +action.participantName }
                        +currentStrings.modalCancelMsgPost
                    }
                }

                div(className = "modal-action") {
                    // Tlačítko Zrušit (zavře dialog)
                    button(className = "btn") {
                        onClick { pendingAction = null }
                        +currentStrings.modalBack
                    }

                    button(className = "btn ${if (action.type == AdminActionType.CONFIRM_PAYMENT) "btn-success" else "btn-error"}") {
                        onClick {
                            scope.launch {
                                when (action.type) {
                                    AdminActionType.CONFIRM_PAYMENT -> {
                                        adminService.markReservationAsPaid(action.reservationId)
                                            .onRight {
                                                toastData = ToastData(currentStrings.toastPaymentConfirmed(action.participantName), ToastType.Success)
                                                refreshTrigger++
                                            }
                                            .onLeft { error ->
                                                toastData = ToastData(currentStrings.errorToast(error.toString()), ToastType.Error)
                                            }
                                    }
                                    AdminActionType.CANCEL_RESERVATION -> {
                                        reservationService.cancelReservation(action.reservationId)
                                            .onRight {
                                                toastData = ToastData(currentStrings.toastReservationCancelled(action.participantName), ToastType.Success)
                                                refreshTrigger++
                                            }
                                            .onLeft { error -> toastData = ToastData(currentStrings.errorToast(error.toString()), ToastType.Error) }
                                    }
                                }
                                pendingAction = null
                            }
                        }
                        if (action.type == AdminActionType.CONFIRM_PAYMENT) +currentStrings.modalConfirmAction else +currentStrings.modalConfirmCancelAction
                    }
                }
            }

            form(className = "modal-backdrop") {
                button {
                    onClick { pendingAction = null }
                    +currentStrings.close
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val reservationCount = (uiState as? AdminEventDetailUiState.Success)?.data?.participants?.size ?: 0
        div(className = "modal modal-open") {
            div(className = "modal-box") {
                h3(className = "font-bold text-lg text-error") { +currentStrings.confirmDeleteTitle }
                p(className = "py-4") { +currentStrings.deleteEventImpact(reservationCount) }
                div(className = "modal-action") {
                    button(className = "btn") { onClick { showDeleteConfirm = false }; +currentStrings.modalBack }
                    button(className = "btn btn-error") {
                        onClick {
                            showDeleteConfirm = false
                            scope.launch {
                                val uuid = Uuid.parse(eventId)
                                val result = if (isSeries)
                                    adminService.deleteEventSeries(uuid)
                                else
                                    adminService.deleteEventInstance(uuid)
                                result
                                    .onRight {
                                        toastData = ToastData(
                                            if (isSeries) currentStrings.toastSeriesDeleted else currentStrings.toastEventDeleted,
                                            ToastType.Success
                                        )
                                        kotlinx.coroutines.delay(500)
                                        router.navigate("/admin/events")
                                    }
                                    .onLeft { toastData = ToastData(currentStrings.errorToast(it.toString()), ToastType.Error) }
                            }
                        }
                        if (isSeries) +currentStrings.deleteSeriesLabel else +currentStrings.deleteEventLabel
                    }
                }
            }
            form(className = "modal-backdrop") {
                button { onClick { showDeleteConfirm = false }; +currentStrings.close }
            }
        }
    }

    val lessonToCancel = cancelLessonPending
    if (lessonToCancel != null) {
        div(className = "modal modal-open") {
            div(className = "modal-box") {
                h3(className = "font-bold text-lg") { +currentStrings.cancelLessonModalTitle }
                p(className = "py-4") {
                    +currentStrings.cancelLessonModalBody(lessonToCancel.startDateTime.date.humanReadable)
                }
                div(className = "modal-action") {
                    button(className = "btn") {
                        onClick { cancelLessonPending = null }
                        +currentStrings.cancel
                    }
                    button(className = "btn btn-error") {
                        onClick {
                            val toCancel = lessonToCancel
                            cancelLessonPending = null
                            scope.launch {
                                adminService.cancelSeriesLesson(toCancel.id)
                                    .onRight {
                                        toastData = ToastData(currentStrings.toastLessonCancelled, ToastType.Success)
                                        lessonsRefreshTrigger++
                                    }
                                    .onLeft { toastData = ToastData(currentStrings.errorToast(it.toString()), ToastType.Error) }
                            }
                        }
                        +currentStrings.cancelLessonButton
                    }
                }
            }
            form(className = "modal-backdrop") {
                button { onClick { cancelLessonPending = null }; +"close" }
            }
        }
    }

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}