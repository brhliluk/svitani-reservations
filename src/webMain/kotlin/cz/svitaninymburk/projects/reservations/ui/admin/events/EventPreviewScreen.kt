package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.events.Event
import cz.svitaninymburk.projects.reservations.ui.events.SeriesCard
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationModal
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.span
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

private sealed interface PreviewState {
    data object Loading : PreviewState
    data class Instance(val event: EventInstance) : PreviewState
    data class Series(val detail: SeriesDetailResponse) : PreviewState
    data class Error(val message: String) : PreviewState
}

@Composable
fun IComponent.EventPreviewScreen(eventId: String, isSeries: Boolean, currentUser: User) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val eventService = getService<EventServiceInterface>(RpcSerializersModules)
    val reservationService = getService<ReservationServiceInterface>(RpcSerializersModules)
    val currentStrings by strings

    var reservationTarget by remember { mutableStateOf<ReservationTarget?>(null) }
    var isWaitlistSignup by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val uuid = try { Uuid.parse(eventId) } catch (_: IllegalArgumentException) { null }

    if (uuid == null) {
        router.navigate("/admin/events")
        return
    }

    val state by produceState<PreviewState>(initialValue = PreviewState.Loading) {
        value = if (!isSeries) {
            eventService.getInstance(uuid)
                .fold(
                    ifLeft = { PreviewState.Error(it.localizedMessage(currentStrings)) },
                    ifRight = { PreviewState.Instance(it) }
                )
        } else {
            eventService.getSeriesDetail(uuid)
                .fold(
                    ifLeft = { PreviewState.Error(it.localizedMessage(currentStrings)) },
                    ifRight = { PreviewState.Series(it) }
                )
        }
    }

    suspend fun submitReservation(target: ReservationTarget, formData: ReservationFormData) {
        isSubmitting = true
        val result = when {
            formData.asWaitlist && target is ReservationTarget.Instance -> reservationService.joinWaitlistInstance(
                request = formData.toCreateInstanceReservationRequest(target.id),
                userId = currentUser.id
            )
            formData.asWaitlist && target is ReservationTarget.Series -> reservationService.joinWaitlistSeries(
                request = formData.toCreateSeriesReservationRequest(target.id),
                userId = currentUser.id
            )
            target is ReservationTarget.Instance -> reservationService.reserveInstance(
                request = formData.toCreateInstanceReservationRequest(target.id),
                userId = currentUser.id
            )
            else -> reservationService.reserveSeries(
                request = formData.toCreateSeriesReservationRequest((target as ReservationTarget.Series).id),
                userId = currentUser.id
            )
        }
        isSubmitting = false
        result
            .onRight { reservation -> router.navigate("/reservation/${reservation.id}") }
            .onLeft { error -> toastMessage = error.localizedMessage(currentStrings) }
    }

    div(className = "min-h-screen bg-base-200 flex flex-col") {

        val isPublished = when (val s = state) {
            is PreviewState.Instance -> s.event.isPublished
            is PreviewState.Series -> s.detail.series.isPublished
            else -> false
        }
        val bannerText = if (isPublished) currentStrings.previewBannerPublished else currentStrings.previewBannerUnpublished
        val bannerClass = if (isPublished) "alert alert-success" else "alert alert-warning"

        div(className = "sticky top-0 z-50 w-full") {
            div(className = "$bannerClass rounded-none flex items-center gap-3 px-4 py-2") {
                span(className = "icon-[heroicons--eye] size-5 shrink-0")
                span(className = "flex-1 font-medium text-sm") { +bannerText }
                button(className = "btn btn-sm btn-ghost gap-1") {
                    onClick { router.navigate(if (isSeries) "/admin/events/series/$eventId" else "/admin/events/instance/$eventId") }
                    span(className = "icon-[heroicons--arrow-left] size-4")
                    +currentStrings.backToAdmin
                }
            }
        }

        div(className = "flex-1 w-full max-w-2xl mx-auto px-4 py-8") {
            when (val s = state) {
                is PreviewState.Loading -> Loading()
                is PreviewState.Error -> div(className = "alert alert-error") { span { +s.message } }
                is PreviewState.Instance -> Event(
                    event = s.event,
                    onClick = { isWaitlistSignup = false; reservationTarget = ReservationTarget.Instance(s.event) },
                    onWaitlistClick = if (s.event.waitlistCapacity > 0) {
                        { isWaitlistSignup = true; reservationTarget = ReservationTarget.Instance(s.event) }
                    } else null
                )
                is PreviewState.Series -> SeriesCard(
                    series = s.detail.series,
                    onSignUpClick = { isWaitlistSignup = false; reservationTarget = ReservationTarget.Series(s.detail.series) }
                )
            }
        }

        ReservationModal(
            target = reservationTarget,
            user = currentUser,
            isSubmitting = isSubmitting,
            asWaitlist = isWaitlistSignup,
            onClose = { reservationTarget = null; isWaitlistSignup = false },
            onSubmit = { target, data -> scope.launch { submitReservation(target, data) } }
        )

        Toast(
            message = toastMessage,
            type = ToastType.Error,
            onDismiss = { toastMessage = null }
        )
    }
}
