package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.repository.audit.AuditRepository
import cz.svitaninymburk.projects.reservations.repository.audit.NewAuditEvent
import cz.svitaninymburk.projects.reservations.repository.event.EventInstancesTable
import cz.svitaninymburk.projects.reservations.repository.payment.PaymentEventsTable
import cz.svitaninymburk.projects.reservations.repository.reservation.ReferenceDbDiscriminator
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutsTable
import cz.svitaninymburk.projects.reservations.repository.wallet.WalletTransactionsTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.util.dbQuery
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import io.ktor.server.application.Application
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * Naplní historii z toho, co v datech už má časové razítko, aby karta „Historie"
 * nebyla u starých akcí prázdná.
 *
 * Zpětně dohledatelné je jen tohle — storna, povýšení z pořadníku ani odeslané
 * maily nikde razítko nemají a od nasazení dál je vede až běžný zápis.
 *
 * Běží jen když je tabulka prázdná: opakovaný start tedy nic nezduplikuje.
 */
fun Application.startAuditBackfill() {
    val auditRepository: AuditRepository by inject()
    val logger = KtorSimpleLogger("AuditBackfill")

    launch(Dispatchers.IO) {
        try {
            if (auditRepository.countAll() > 0L) return@launch

            val events = buildList {
                addAll(reservationsCreated())
                addAll(paymentsPaired())
                addAll(lessonOptOuts())
                addAll(walletRefunds())
            }.sortedBy { it.occurredAt }

            if (events.isEmpty()) {
                logger.info("Audit backfill: nic k doplnění")
                return@launch
            }

            auditRepository.recordAll(events)
            logger.info("Audit backfill: doplněno ${events.size} historických záznamů")
        } catch (e: Exception) {
            logger.error("Audit backfill failed (non-fatal)", e)
        }
    }
}

/** série, do které lekce patří — kvůli tomu, aby záznam viděl i detail kurzu */
private suspend fun seriesByInstance(): Map<Uuid, Uuid?> = dbQuery {
    EventInstancesTable.selectAll().associate {
        it[EventInstancesTable.id] to it[EventInstancesTable.seriesId]
    }
}

private suspend fun reservationsCreated(): List<NewAuditEvent> {
    val seriesOf = seriesByInstance()
    return dbQuery {
        ReservationsTable.selectAll().map { row ->
            val refId = row[ReservationsTable.referenceId]
            val isSeries = row[ReservationsTable.referenceType] == ReferenceDbDiscriminator.SERIES
            NewAuditEvent(
                type = AuditEventType.RESERVATION_CREATED,
                actorType = AuditActorType.SYSTEM,
                actorLabel = BACKFILL_ACTOR,
                subjectLabel = row[ReservationsTable.contactName],
                seriesId = if (isSeries) refId else seriesOf[refId],
                instanceId = if (isSeries) null else refId,
                reservationId = row[ReservationsTable.id],
                amount = row[ReservationsTable.totalPrice],
                detail = "${row[ReservationsTable.seatCount]}× místo (doplněno zpětně)",
                occurredAt = row[ReservationsTable.createdAt],
            )
        }
    }
}

private suspend fun paymentsPaired(): List<NewAuditEvent> {
    val seriesOf = seriesByInstance()
    return dbQuery {
        PaymentEventsTable
            .join(
                ReservationsTable, JoinType.LEFT,
                onColumn = PaymentEventsTable.reservationId,
                otherColumn = ReservationsTable.id,
            )
            .selectAll()
            .map { row ->
                val refId = row.getOrNull(ReservationsTable.referenceId)
                val isSeries = row.getOrNull(ReservationsTable.referenceType) == ReferenceDbDiscriminator.SERIES
                NewAuditEvent(
                    // payment_events nerozlišuje nedoplatek, takže se vše mapuje na spárováno
                    type = when (row[PaymentEventsTable.paymentSource]) {
                        PaymentEvent.Source.MANUAL_ADMIN -> AuditEventType.PAYMENT_PAIRED_MANUAL
                        PaymentEvent.Source.AUTO_FIO -> AuditEventType.PAYMENT_PAIRED_AUTO
                    },
                    actorType = AuditActorType.SYSTEM,
                    actorLabel = BACKFILL_ACTOR,
                    subjectLabel = row.getOrNull(ReservationsTable.contactName) ?: "",
                    seriesId = if (isSeries) refId else refId?.let { seriesOf[it] },
                    instanceId = if (isSeries) null else refId,
                    reservationId = row[PaymentEventsTable.reservationId],
                    amount = row[PaymentEventsTable.amount],
                    detail = "Doplněno zpětně z payment_events",
                    occurredAt = row[PaymentEventsTable.processedAt],
                )
            }
    }
}

private suspend fun lessonOptOuts(): List<NewAuditEvent> {
    val seriesOf = seriesByInstance()
    return dbQuery {
        SeriesLessonOptOutsTable
            .join(
                ReservationsTable, JoinType.LEFT,
                onColumn = SeriesLessonOptOutsTable.reservationId,
                otherColumn = ReservationsTable.id,
            )
            .selectAll()
            .map { row ->
                val instanceId = row[SeriesLessonOptOutsTable.instanceId]
                NewAuditEvent(
                    type = AuditEventType.RESERVATION_LESSON_OPT_OUT,
                    actorType = AuditActorType.SYSTEM,
                    actorLabel = BACKFILL_ACTOR,
                    subjectLabel = row.getOrNull(ReservationsTable.contactName) ?: "",
                    seriesId = seriesOf[instanceId],
                    instanceId = instanceId,
                    reservationId = row[SeriesLessonOptOutsTable.reservationId],
                    detail = if (row[SeriesLessonOptOutsTable.isLateCancellation]) "pozdní omluvenka" else "omluvenka z lekce",
                    occurredAt = row[SeriesLessonOptOutsTable.optedOutAt],
                )
            }
    }
}

private val REFUND_REASONS = setOf(
    WalletTransactionReason.CANCELLATION_REFUND,
    WalletTransactionReason.LESSON_OPT_OUT_REFUND,
    WalletTransactionReason.RESERVATION_DEBIT_REVERSAL,
)

private suspend fun walletRefunds(): List<NewAuditEvent> {
    val seriesOf = seriesByInstance()
    return dbQuery {
        WalletTransactionsTable
            .join(
                ReservationsTable, JoinType.LEFT,
                onColumn = WalletTransactionsTable.reservationId,
                otherColumn = ReservationsTable.id,
            )
            .selectAll()
            .filter { it[WalletTransactionsTable.reason] in REFUND_REASONS }
            // Sezónní reset a ruční úpravy nemají rezervaci, takže by se neměly kam
            // pověsit — karta je per akce, tam by je nikdo nikdy neuviděl.
            .filter { it[WalletTransactionsTable.reservationId] != null }
            .map { row ->
                val refId = row.getOrNull(ReservationsTable.referenceId)
                val isSeries = row.getOrNull(ReservationsTable.referenceType) == ReferenceDbDiscriminator.SERIES
                NewAuditEvent(
                    type = AuditEventType.PAYMENT_REFUNDED,
                    actorType = AuditActorType.SYSTEM,
                    actorLabel = BACKFILL_ACTOR,
                    subjectLabel = row.getOrNull(ReservationsTable.contactName) ?: "",
                    seriesId = if (isSeries) refId else refId?.let { seriesOf[it] },
                    instanceId = if (isSeries) null else refId,
                    reservationId = row[WalletTransactionsTable.reservationId],
                    amount = row[WalletTransactionsTable.amount],
                    detail = row[WalletTransactionsTable.reason].name,
                    occurredAt = row[WalletTransactionsTable.createdAt],
                )
            }
    }
}

private const val BACKFILL_ACTOR = "doplněno zpětně"
