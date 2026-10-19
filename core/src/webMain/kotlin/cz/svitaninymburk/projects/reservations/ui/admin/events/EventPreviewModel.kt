package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.EventPreviewQueries
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationSubmittingModel
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitUseCase
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitter
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

sealed interface EventPreviewState {
    data object Loading : EventPreviewState
    data class Instance(val event: EventInstance) : EventPreviewState
    data class Series(val detail: SeriesDetailResponse) : EventPreviewState
    data class Error(val message: String) : EventPreviewState
}

/**
 * Admin náhled akce očima návštěvníka — ukazuje stejné karty jako rozcestník,
 * včetně rezervačního formuláře, aby šlo proklikat i nezveřejněnou akci.
 */
class EventPreviewModel(
    scope: CoroutineScope,
    private val queries: EventPreviewQueries,
    submitter: ReservationSubmitter,
    router: Router,
    private val eventId: Uuid,
    private val isSeries: Boolean,
) : ReservationSubmittingModel(scope, submitter, { router.navigate("/reservation/${it.id}") }) {

    var state by mutableStateOf<EventPreviewState>(EventPreviewState.Loading); private set
    var reservationTarget by mutableStateOf<ReservationTarget?>(null); private set
    var isWaitlistSignup by mutableStateOf(false); private set

    /** Podle toho se přepíná pruh nahoře: zveřejněno / jen náhled. */
    val isPublished: Boolean
        get() = when (val s = state) {
            is EventPreviewState.Instance -> s.event.isPublished
            is EventPreviewState.Series -> s.detail.series.isPublished
            else -> false
        }

    fun load() {
        scope.launch {
            try {
                state = if (isSeries) {
                    queries.seriesDetail(eventId).fold(
                        ifLeft = { EventPreviewState.Error(it.localizedMessage(currentStrings)) },
                        ifRight = { EventPreviewState.Series(it) },
                    )
                } else {
                    queries.instance(eventId).fold(
                        ifLeft = { EventPreviewState.Error(it.localizedMessage(currentStrings)) },
                        ifRight = { EventPreviewState.Instance(it) },
                    )
                }
            } catch (e: Exception) {
                state = EventPreviewState.Error(currentStrings.loadingError(e.message ?: "unknown"))
            }
        }
    }

    fun openReservation(target: ReservationTarget, asWaitlist: Boolean = false) {
        isWaitlistSignup = asWaitlist
        reservationTarget = target
    }

    fun closeReservation() {
        reservationTarget = null
        isWaitlistSignup = false
    }
}

fun IComponent.buildEventPreviewModel(
    scope: CoroutineScope,
    router: Router,
    eventId: Uuid,
    isSeries: Boolean,
): EventPreviewModel {
    val event = getService<EventServiceInterface>(RpcSerializersModules)
    val reservations = getService<ReservationServiceInterface>(RpcSerializersModules)
    return EventPreviewModel(
        scope = scope,
        queries = EventPreviewQueries(event),
        submitter = ReservationSubmitUseCase(reservations),
        router = router,
        eventId = eventId,
        isSeries = isSeries,
    )
}
