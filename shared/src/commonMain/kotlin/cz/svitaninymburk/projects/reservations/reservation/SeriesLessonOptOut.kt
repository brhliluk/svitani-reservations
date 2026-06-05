package cz.svitaninymburk.projects.reservations.reservation

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class SeriesLessonOptOut(
    val id: Uuid,
    val reservationId: Uuid,
    val instanceId: Uuid,
    val optedOutAt: Instant,
    val isLateCancellation: Boolean,
)
