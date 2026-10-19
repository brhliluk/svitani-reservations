package cz.svitaninymburk.projects.reservations.repository.audit

import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.audit.AuditEvent
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.audit.AuditOutcome
import cz.svitaninymburk.projects.reservations.util.dbQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

/**
 * Historie dění kolem akcí a rezervací.
 *
 * Schválně **bez cizích klíčů**: `payment_events.reservation_id` má `CASCADE`, takže
 * smazání rezervace dnes zahodí i její platební historii. Audit log má smysl jen
 * tehdy, když mazání přežije — proto jsou vazby jen volné UUID a čitelné popisky
 * (`actor_label`, `subject_label`) se ukládají denormalizovaně jako text.
 *
 * Zápis je append-only; jediné mazání dělá retenční job (`plugins/AuditRetention.kt`).
 */
object AuditEventsTable : Table("audit_events") {
    val id = uuid("id").autoGenerate()
    val occurredAt = timestamp("occurred_at")
    val category = enumerationByName("category", 20, AuditCategory::class)
    val type = enumerationByName("type", 50, AuditEventType::class)

    /** U záznamů jednotlivých lekcí se vyplňuje spolu s [instanceId], aby detail kurzu viděl i dění v lekcích. */
    val seriesId = uuid("series_id").nullable()
    val instanceId = uuid("instance_id").nullable()
    val reservationId = uuid("reservation_id").nullable()
    /** Kód, ne id — stejná logika jako u ostatních denormalizovaných popisků. */
    val walletCode = varchar("wallet_code", 20).nullable()

    val actorType = enumerationByName("actor_type", 20, AuditActorType::class)
    val actorLabel = varchar("actor_label", 255)
    val subjectLabel = varchar("subject_label", 255)

    val outcome = enumerationByName("outcome", 20, AuditOutcome::class).nullable()
    val recipient = varchar("recipient", 255).nullable()
    val amount = double("amount").nullable()
    val detail = text("detail").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_audit_events_instance", false, instanceId, occurredAt)
        index("ix_audit_events_series", false, seriesId, occurredAt)
        index("ix_audit_events_reservation", false, reservationId, occurredAt)
        index("ix_audit_events_occurred_at", false, occurredAt)
    }
}

fun ResultRow.toAuditEvent() = AuditEvent(
    id = this[AuditEventsTable.id],
    occurredAt = this[AuditEventsTable.occurredAt],
    type = this[AuditEventsTable.type],
    seriesId = this[AuditEventsTable.seriesId],
    instanceId = this[AuditEventsTable.instanceId],
    reservationId = this[AuditEventsTable.reservationId],
    walletCode = this[AuditEventsTable.walletCode],
    actorType = this[AuditEventsTable.actorType],
    actorLabel = this[AuditEventsTable.actorLabel],
    subjectLabel = this[AuditEventsTable.subjectLabel],
    outcome = this[AuditEventsTable.outcome],
    recipient = this[AuditEventsTable.recipient],
    amount = this[AuditEventsTable.amount],
    detail = this[AuditEventsTable.detail],
)

data class NewAuditEvent(
    val type: AuditEventType,
    val actorType: AuditActorType,
    val actorLabel: String,
    val subjectLabel: String,
    val seriesId: Uuid? = null,
    val instanceId: Uuid? = null,
    val reservationId: Uuid? = null,
    val walletCode: String? = null,
    val outcome: AuditOutcome? = null,
    val recipient: String? = null,
    val amount: Double? = null,
    val detail: String? = null,
    val occurredAt: Instant? = null,
)

interface AuditRepository {
    suspend fun record(event: NewAuditEvent)

    /** Jeden záznam podle id — potřebuje ho přeposlání mailu z historie. */
    suspend fun findById(id: Uuid): AuditEvent?

    /** Hromadný zápis pro backfill — jedna transakce místo tisíce. */
    suspend fun recordAll(events: List<NewAuditEvent>)

    /**
     * Záznamy k jedné akci.
     *
     * Pro kurz (`isSeries`) bere i dění z jeho lekcí — ty mají vyplněné `series_id`.
     * Pro lekci kurzu se navíc přiberou záznamy vedené na celém kurzu
     * ([parentSeriesId], `instance_id IS NULL`): účastníci jsou zapsaní na kurz,
     * takže bez toho by detail lekce o nich neukázal vůbec nic.
     */
    suspend fun findForEvent(
        eventId: Uuid,
        isSeries: Boolean,
        category: AuditCategory?,
        page: Int,
        pageSize: Int,
        parentSeriesId: Uuid? = null,
    ): List<AuditEvent>

    suspend fun countForEvent(
        eventId: Uuid,
        isSeries: Boolean,
        category: AuditCategory?,
        parentSeriesId: Uuid? = null,
    ): Long

    suspend fun countAll(): Long

    /** Vrací počet smazaných řádků. */
    suspend fun deleteOlderThan(cutoff: Instant): Int
}

class ExposedAuditRepository(private val database: Database? = null) : AuditRepository {

    /** Stejně jako `dbQuery`, jen umí cílit na konkrétní DB — kvůli testům. */
    private suspend fun <T> query(block: suspend () -> T): T =
        if (database == null) dbQuery(block)
        else withContext(Dispatchers.IO) { suspendTransaction(db = database) { block() } }

    override suspend fun record(event: NewAuditEvent): Unit = query {
        AuditEventsTable.insert { it.apply(event) }
        Unit
    }

    override suspend fun recordAll(events: List<NewAuditEvent>): Unit = query {
        if (events.isEmpty()) return@query
        AuditEventsTable.batchInsert(events, shouldReturnGeneratedValues = false) { event ->
            this.apply(event)
        }
        Unit
    }

    override suspend fun findById(id: Uuid): AuditEvent? = query {
        AuditEventsTable.selectAll().where { AuditEventsTable.id eq id }.singleOrNull()?.toAuditEvent()
    }

    override suspend fun findForEvent(
        eventId: Uuid,
        isSeries: Boolean,
        category: AuditCategory?,
        page: Int,
        pageSize: Int,
        parentSeriesId: Uuid?,
    ): List<AuditEvent> = query {
        AuditEventsTable.selectAll()
            .where { eventFilter(eventId, isSeries, category, parentSeriesId) }
            .orderBy(AuditEventsTable.occurredAt, SortOrder.DESC)
            .limit(pageSize)
            .offset(page.toLong() * pageSize)
            .map { it.toAuditEvent() }
    }

    override suspend fun countForEvent(
        eventId: Uuid,
        isSeries: Boolean,
        category: AuditCategory?,
        parentSeriesId: Uuid?,
    ): Long = query {
        AuditEventsTable.selectAll().where { eventFilter(eventId, isSeries, category, parentSeriesId) }.count()
    }

    override suspend fun countAll(): Long = query { AuditEventsTable.selectAll().count() }

    override suspend fun deleteOlderThan(cutoff: Instant): Int = query {
        AuditEventsTable.deleteWhere { occurredAt less cutoff }
    }
}

private fun UpdateBuilder<*>.apply(event: NewAuditEvent) {
    this[AuditEventsTable.occurredAt] = event.occurredAt ?: Clock.System.now()
    this[AuditEventsTable.category] = event.type.category
    this[AuditEventsTable.type] = event.type
    this[AuditEventsTable.seriesId] = event.seriesId
    this[AuditEventsTable.instanceId] = event.instanceId
    this[AuditEventsTable.reservationId] = event.reservationId
    this[AuditEventsTable.walletCode] = event.walletCode?.take(20)
    this[AuditEventsTable.actorType] = event.actorType
    this[AuditEventsTable.actorLabel] = event.actorLabel.take(255)
    this[AuditEventsTable.subjectLabel] = event.subjectLabel.take(255)
    this[AuditEventsTable.outcome] = event.outcome
    this[AuditEventsTable.recipient] = event.recipient?.take(255)
    this[AuditEventsTable.amount] = event.amount
    this[AuditEventsTable.detail] = event.detail
}

private fun eventFilter(
    eventId: Uuid,
    isSeries: Boolean,
    category: AuditCategory?,
    parentSeriesId: Uuid?,
): Op<Boolean> {
    val subject = when {
        isSeries -> AuditEventsTable.seriesId eq eventId
        parentSeriesId != null -> (AuditEventsTable.instanceId eq eventId) or
                ((AuditEventsTable.seriesId eq parentSeriesId) and AuditEventsTable.instanceId.isNull())
        else -> AuditEventsTable.instanceId eq eventId
    }
    return if (category == null) subject else subject and (AuditEventsTable.category eq category)
}
