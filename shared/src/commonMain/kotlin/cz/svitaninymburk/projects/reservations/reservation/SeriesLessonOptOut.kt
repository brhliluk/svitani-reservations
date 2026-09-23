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
    /**
     * Kolik kreditu za tuhle omluvenku skutečně odešlo do peněženky. Drží se u
     * omluvenky, ne jen v součtu transakcí peněženky: pod stejným důvodem chodí
     * i kredit za lekci zrušenou adminem, takže ze součtu nejde poznat, co patří
     * které omluvence — a vzetí zpět i dodatečná vratka po zaplacení to vědět musí.
     *
     * null = omluvenka z doby před zavedením sloupce, u které se částku nepodařilo
     * dohledat. S takovou se nic dodatečně nedoplácí (mohla být proplacená).
     */
    val refundedAmount: Double? = 0.0,
)
