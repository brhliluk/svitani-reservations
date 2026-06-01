package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonItem
import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.dialogRef
import dev.kilua.html.h1
import dev.kilua.html.h3
import dev.kilua.html.main
import dev.kilua.html.p
import dev.kilua.html.span
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Composable
fun IComponent.MyReservationsScreen(userId: Uuid, onBackClick: () -> Unit) {
    val currentStrings by strings

    div(className = "min-h-screen bg-base-200 flex flex-col font-sans") {
        main(className = "flex-1 w-full max-w-5xl mx-auto px-3 py-4 sm:px-4 sm:py-8 flex flex-col gap-4 sm:gap-6") {

            button(className = "btn btn-ghost btn-sm self-start gap-2 min-h-11") {
                onClick { onBackClick() }
                span(className = "icon-[heroicons--arrow-left] size-5")
                +currentStrings.backToDashboard
            }

            div(className = "flex items-center gap-3") {
                span(className = "icon-[heroicons--ticket] size-7 text-primary")
                h1(className = "text-2xl sm:text-3xl font-bold text-base-content") {
                    +currentStrings.myReservations
                }
            }

            MyReservationsList(userId)
        }
    }
}

@Composable
fun IComponent.MyReservationsList(userId: Uuid) {
    val currentStrings by strings
    val router = Router.current
    val reservationService = getService<AuthenticatedReservationServiceInterface>(RpcSerializersModules)

    var retryTrigger by remember { mutableStateOf(0) }

    val uiState by produceState<MyReservationsUiState>(
        initialValue = MyReservationsUiState.Loading,
        key1 = retryTrigger,
        key2 = userId,
    ) {
        value = MyReservationsUiState.Loading
        try {
            reservationService.getReservations(userId)
                .onRight { items -> value = MyReservationsUiState.Success(items) }
                .onLeft { error -> value = MyReservationsUiState.Error(error.localizedMessage(currentStrings)) }
        } catch (e: Exception) {
            value = MyReservationsUiState.Error(currentStrings.loadingError(e.message ?: "unknown"))
        }
    }

    when (val state = uiState) {
        is MyReservationsUiState.Loading -> Loading()
        is MyReservationsUiState.Error -> {
            div(className = "alert alert-error") {
                span(className = "icon-[heroicons--exclamation-circle] size-6")
                span { +state.message }
                button(className = "btn btn-sm min-h-11") {
                    onClick { retryTrigger++ }
                    +currentStrings.retry
                }
            }
        }
        is MyReservationsUiState.Success -> {
            if (state.items.isEmpty()) {
                div(className = "alert bg-base-100 shadow-sm animate-fade-in") {
                    span(className = "icon-[heroicons--information-circle] size-6 text-info")
                    +currentStrings.noUpcomingReservations
                }
            } else {
                div(className = "flex flex-col gap-3 animate-fade-in") {
                    state.items.forEach { item ->
                        ReservationCard(item, onCardClick = { router.navigate("/reservation/${item.id}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun IComponent.ReservationCard(item: MyReservationListItem, onCardClick: () -> Unit) {
    val currentStrings by strings
    val isPaid = item.status == Reservation.Status.CONFIRMED
    val isOnSite = item.paymentType == PaymentInfo.Type.ON_SITE
    val scope = rememberCoroutineScope()

    val authenticatedService = getService<AuthenticatedReservationServiceInterface>(RpcSerializersModules)
    val reservationService = getService<ReservationServiceInterface>(RpcSerializersModules)

    var isExpanded by remember { mutableStateOf(false) }
    var lessonsState by remember { mutableStateOf<LessonsLoadState>(LessonsLoadState.Idle) }
    var lessonsRefreshTrigger by remember { mutableStateOf(0) }

    var toastData by remember { mutableStateOf<ToastData?>(null) }
    var pendingOptOutLesson by remember { mutableStateOf<SeriesLessonItem?>(null) }

    // Load lessons when expanded
    LaunchedEffect(isExpanded, lessonsRefreshTrigger) {
        if (isExpanded && item.isSeries) {
            lessonsState = LessonsLoadState.Loading
            authenticatedService.getSeriesReservationDetail(item.id)
                .onRight { detail -> lessonsState = LessonsLoadState.Success(detail.lessons) }
                .onLeft { error -> lessonsState = LessonsLoadState.Error(error.localizedMessage(currentStrings)) }
        }
    }

    // Opt-out confirmation dialog
    val optOutDialog = dialogRef(className = "modal") {
        div(className = "modal-box") {
            h3(className = "font-bold text-lg text-warning flex items-center gap-2") {
                span(className = "icon-[heroicons--exclamation-triangle] size-6")
                +currentStrings.lessonOptOutConfirmTitle
            }

            val lesson = pendingOptOutLesson
            if (lesson != null) {
                p(className = "py-4") {
                    +currentStrings.lessonOptOutConfirmBody(lesson.startDateTime.humanReadable)
                }
            }

            div(className = "modal-action") {
                button(className = "btn") {
                    onClick {
                        this@dialogRef.element.close()
                        pendingOptOutLesson = null
                    }
                    +currentStrings.cancel
                }

                button(className = "btn btn-warning text-white") {
                    onClick {
                        val pendingLesson = pendingOptOutLesson
                        this@dialogRef.element.close()
                        pendingOptOutLesson = null

                        if (pendingLesson != null) {
                            scope.launch {
                                reservationService.cancelReservation(
                                    reservationId = item.id,
                                    instanceId = pendingLesson.instanceId,
                                ).onRight {
                                    toastData = ToastData(currentStrings.toastLessonOptOut, ToastType.Success)
                                    lessonsRefreshTrigger++
                                }.onLeft { error ->
                                    toastData = ToastData(currentStrings.errorToast(error.localizedMessage(currentStrings)), ToastType.Error)
                                }
                            }
                        }
                    }
                    +currentStrings.lessonOptOut
                }
            }
        }
        onClick { event -> if (event.target == this@dialogRef.element) this@dialogRef.element.close() }
    }

    div(className = "card bg-base-100 shadow-sm border border-base-200 hover:shadow-md transition-shadow") {
        div(className = "card-body p-4 sm:p-5 gap-3") {

            // Card header — clickable title area for navigation
            div(className = "${if (!item.isSeries) "cursor-pointer" else ""}") {
                if (!item.isSeries) onClick { onCardClick() }
                div(className = "flex flex-col sm:flex-row justify-between gap-2 sm:items-start") {
                    div(className = "flex flex-col gap-1 min-w-0") {
                        div(
                            className = "font-bold text-base sm:text-lg text-base-content truncate ${if (item.isSeries) "cursor-pointer hover:text-primary transition-colors" else ""}"
                        ) {
                            if (item.isSeries) onClick { onCardClick() }
                            +item.eventTitle
                        }
                        div(className = "flex items-center gap-2 text-sm text-base-content/60") {
                            span(className = "icon-[heroicons--calendar] size-4 shrink-0")
                            span { +item.startDateTime.humanReadable }
                        }
                    }
                    div(className = "flex flex-col items-start sm:items-end gap-1 shrink-0") {
                        when {
                            isPaid -> div(className = "badge badge-success gap-1") {
                                span(className = "icon-[heroicons--check] size-3")
                                +currentStrings.paid
                            }
                            isOnSite -> div(className = "badge badge-info badge-outline gap-1") {
                                span(className = "icon-[heroicons--banknotes] size-3")
                                +currentStrings.statusOnSiteBadge
                            }
                            else -> div(className = "badge badge-warning gap-1") {
                                span(className = "icon-[heroicons--clock] size-3")
                                +currentStrings.statusWaiting
                            }
                        }
                    }
                }
            }

            div(className = "flex flex-wrap items-center justify-between gap-x-4 gap-y-1 text-sm") {
                div(className = "flex items-center gap-1 text-base-content/70") {
                    span(className = "icon-[heroicons--user-group] size-4")
                    +"${item.seatCount} ${currentStrings.persons}"
                }
                div(className = "flex items-center gap-3") {
                    span(className = "text-base-content/60") {
                        if (isOnSite) +currentStrings.paymentMethodCash else +currentStrings.bankTransfer
                    }
                    span(className = "font-bold text-primary") {
                        +"${item.totalPrice} ${currentStrings.currency}"
                    }
                }
            }

            // Series expand button
            if (item.isSeries) {
                div(className = "border-t border-base-200 pt-2 mt-1") {
                    button(className = "btn btn-ghost btn-xs gap-1 text-base-content/60 hover:text-primary") {
                        onClick {
                            isExpanded = !isExpanded
                            if (isExpanded) lessonsState = LessonsLoadState.Loading
                        }
                        span(className = "icon-[heroicons--${if (isExpanded) "chevron-up" else "chevron-down"}] size-4")
                        +if (isExpanded) currentStrings.showLess else currentStrings.courseLessons
                    }
                }

                // Expanded lessons area
                if (isExpanded) {
                    div(className = "flex flex-col gap-2 mt-2 animate-fade-in") {
                        when (val state = lessonsState) {
                            is LessonsLoadState.Idle, is LessonsLoadState.Loading -> {
                                div(className = "flex justify-center py-3") {
                                    span(className = "loading loading-spinner loading-sm text-primary")
                                }
                            }
                            is LessonsLoadState.Error -> {
                                div(className = "alert alert-error alert-sm py-2 text-sm") {
                                    span(className = "icon-[heroicons--exclamation-circle] size-4")
                                    span { +state.message }
                                }
                            }
                            is LessonsLoadState.Success -> {
                                if (state.lessons.isEmpty()) {
                                    p(className = "text-sm text-base-content/50 italic py-2") { +currentStrings.noLessonsYet }
                                } else {
                                    state.lessons.forEach { lesson ->
                                        LessonRow(
                                            lesson = lesson,
                                            onOptOutClick = {
                                                pendingOptOutLesson = lesson
                                                optOutDialog.element.showModal()
                                            }
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

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}

@Composable
private fun IComponent.LessonRow(lesson: SeriesLessonItem, onOptOutClick: () -> Unit) {
    val currentStrings by strings
    val isPast = lesson.startDateTime.toInstant(TimeZone.currentSystemDefault()) < Clock.System.now()

    div(className = "flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-2 rounded-lg bg-base-200/50 text-sm") {
        div(className = "flex items-center gap-2 text-base-content/70") {
            span(className = "icon-[heroicons--clock] size-4 shrink-0")
            span { +lesson.startDateTime.humanReadable }
            when {
                lesson.isCancelled -> div(className = "badge badge-error badge-sm gap-1") {
                    +currentStrings.lessonCancelledBadge
                }
                lesson.isOptedOut -> {
                    div(className = "badge badge-warning badge-sm gap-1") {
                        +currentStrings.lessonOptedOut
                    }
                    if (lesson.isLateCancellation) {
                        div(className = "badge badge-ghost badge-sm gap-1") {
                            +currentStrings.lessonOptOutLate
                        }
                    }
                }
                else -> {}
            }
        }

        if (!lesson.isCancelled && !lesson.isOptedOut && !isPast) {
            button(className = "btn btn-outline btn-warning btn-xs gap-1 shrink-0") {
                span(className = "icon-[heroicons--arrow-left-end-on-rectangle] size-3")
                +currentStrings.lessonOptOut
                onClick { onOptOutClick() }
            }
        }
    }
}

private sealed interface LessonsLoadState {
    data object Idle : LessonsLoadState
    data object Loading : LessonsLoadState
    data class Success(val lessons: List<SeriesLessonItem>) : LessonsLoadState
    data class Error(val message: String) : LessonsLoadState
}

private sealed interface MyReservationsUiState {
    data object Loading : MyReservationsUiState
    data class Success(val items: List<MyReservationListItem>) : MyReservationsUiState
    data class Error(val message: String) : MyReservationsUiState
}
