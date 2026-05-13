package cz.svitaninymburk.projects.reservations.repository.payment

import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import kotlin.time.Clock
import kotlin.uuid.Uuid

class InMemoryPaymentEventRepository : PaymentEventRepository {
    private val events = java.util.Collections.synchronizedList(mutableListOf<PaymentEvent>())

    override suspend fun insert(event: NewPaymentEvent) {
        events.add(PaymentEvent(
            id = Uuid.random().toString(),
            reservationId = event.reservationId.toString(),
            contactName = "",
            amount = event.amount,
            currency = event.currency,
            type = event.type,
            source = event.source,
            processedAt = Clock.System.now(),
        ))
    }

    override suspend fun findAll(page: Int, pageSize: Int): List<PaymentEvent> =
        events.sortedByDescending { it.processedAt }
              .drop(page * pageSize)
              .take(pageSize)

    override suspend fun countAll(): Long = events.size.toLong()

    fun insertedEvents(): List<PaymentEvent> = synchronized(events) { events.toList() }

    fun seed(event: PaymentEvent) { events.add(event) }
}
