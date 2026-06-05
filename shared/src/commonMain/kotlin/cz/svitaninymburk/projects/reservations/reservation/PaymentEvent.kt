package cz.svitaninymburk.projects.reservations.reservation

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PaymentEvent(
    val id: String,
    val reservationId: String,
    val contactName: String,
    val amount: Double,
    val currency: String,
    val type: PaymentInfo.Type,
    val source: Source,
    val processedAt: Instant,
) {
    @Serializable
    enum class Source { AUTO_FIO, MANUAL_ADMIN }
}
