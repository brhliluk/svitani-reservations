package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.util.dbQuery
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.json.json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

// --- 1. EVENT DEFINITIONS ---
object EventDefinitionsTable : Table("event_definitions") {
    val id = uuid("id").autoGenerate()
    val title = varchar("title", 255)
    val description = text("description")
    val defaultPrice = double("default_price")
    val defaultCapacity = integer("default_capacity")
    val defaultDurationMs = long("default_duration_ms")

    val allowedPaymentTypes = json<List<PaymentInfo.Type>>("allowed_payment_types", Json)
    val customFields = json<List<CustomFieldDefinition>>("custom_fields", Json)

    val recurrenceType = enumerationByName("recurrence_type", 20, RecurrenceType::class)
    val recurrenceEndDate = timestamp("recurrence_end_date").nullable()

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toEventDefinition(): EventDefinition = EventDefinition(
    id = this[EventDefinitionsTable.id],
    title = this[EventDefinitionsTable.title],
    description = this[EventDefinitionsTable.description],
    defaultPrice = this[EventDefinitionsTable.defaultPrice],
    defaultCapacity = this[EventDefinitionsTable.defaultCapacity],
    defaultDuration = this[EventDefinitionsTable.defaultDurationMs].milliseconds,
    allowedPaymentTypes = this[EventDefinitionsTable.allowedPaymentTypes],
    recurrenceType = this[EventDefinitionsTable.recurrenceType],
    recurrenceEndDate = this[EventDefinitionsTable.recurrenceEndDate],
    customFields = this[EventDefinitionsTable.customFields]
)


// --- 2. EVENT SERIES ---
object EventSeriesTable : Table("event_series") {
    val id = uuid("id").autoGenerate()
    val definitionId = reference(
        name = "definition_id",
        refColumn = EventDefinitionsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val title = varchar("title", 255)
    val description = text("description")
    val price = double("price")
    val capacity = integer("capacity")
    val occupiedSpots = integer("occupied_spots").default(0)
    val startDate = date("start_date")
    val endDate = date("end_date")
    val lessonCount = integer("lesson_count")

    val allowedPaymentTypes = json<List<PaymentInfo.Type>>("allowed_payment_types", Json)
    val customFields = json<List<CustomFieldDefinition>>("custom_fields", Json)

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toEventSeries(): EventSeries = EventSeries(
    id = this[EventSeriesTable.id],
    definitionId = this[EventSeriesTable.definitionId],
    title = this[EventSeriesTable.title],
    description = this[EventSeriesTable.description],
    price = this[EventSeriesTable.price],
    capacity = this[EventSeriesTable.capacity],
    occupiedSpots = this[EventSeriesTable.occupiedSpots],
    startDate = this[EventSeriesTable.startDate],
    endDate = this[EventSeriesTable.endDate],
    lessonCount = this[EventSeriesTable.lessonCount],
    allowedPaymentTypes = this[EventSeriesTable.allowedPaymentTypes],
    customFields = this[EventSeriesTable.customFields]
)


// --- 3. EVENT INSTANCES ---
object EventInstancesTable : Table("event_instances") {
    val id = uuid("id").autoGenerate()
    val definitionId = reference(
        name = "definition_id",
        refColumn = EventDefinitionsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val seriesId = optReference(
        name = "series_id",
        refColumn = EventSeriesTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val title = varchar("title", 255)
    val description = text("description")
    val startDateTime = datetime("start_date_time")
    val endDateTime = datetime("end_date_time")
    val price = double("price")
    val capacity = integer("capacity")
    val occupiedSpots = integer("occupied_spots").default(0)
    val isCancelled = bool("is_cancelled").default(false)

    val allowedPaymentTypes = json<List<PaymentInfo.Type>>("allowed_payment_types", Json)
    val customFields = json<List<CustomFieldDefinition>>("custom_fields", Json)

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toEventInstance(): EventInstance = EventInstance(
    id = this[EventInstancesTable.id],
    definitionId = this[EventInstancesTable.definitionId],
    seriesId = this[EventInstancesTable.seriesId],
    title = this[EventInstancesTable.title],
    description = this[EventInstancesTable.description],
    startDateTime = this[EventInstancesTable.startDateTime],
    endDateTime = this[EventInstancesTable.endDateTime],
    price = this[EventInstancesTable.price],
    capacity = this[EventInstancesTable.capacity],
    occupiedSpots = this[EventInstancesTable.occupiedSpots],
    isCancelled = this[EventInstancesTable.isCancelled],
    allowedPaymentTypes = this[EventInstancesTable.allowedPaymentTypes],
    customFields = this[EventInstancesTable.customFields]
)

class ExposedEventDefinitionRepository : EventDefinitionRepository {

    override suspend fun get(id: Uuid): EventDefinition? = dbQuery {
        EventDefinitionsTable.selectAll()
            .where { EventDefinitionsTable.id eq id }
            .map { it.toEventDefinition() }
            .singleOrNull()
    }

    override suspend fun getAll(definitionIds: List<Uuid>?): List<EventDefinition> = dbQuery {
        val query = EventDefinitionsTable.selectAll()
        if (definitionIds != null) {
            query.where { EventDefinitionsTable.id inList definitionIds }
        }
        query.map { it.toEventDefinition() }
    }

    override suspend fun create(event: EventDefinition): EventDefinition = dbQuery {
        EventDefinitionsTable.insert { row ->
            row[id] = event.id
            row[title] = event.title
            row[description] = event.description
            row[defaultPrice] = event.defaultPrice
            row[defaultCapacity] = event.defaultCapacity
            row[defaultDurationMs] = event.defaultDuration.inWholeMilliseconds // Převod Duration na Long
            row[allowedPaymentTypes] = event.allowedPaymentTypes
            row[customFields] = event.customFields
            row[recurrenceType] = event.recurrenceType
            row[recurrenceEndDate] = event.recurrenceEndDate
        }
        event
    }

    override suspend fun update(event: EventDefinition): EventDefinition = dbQuery {
        EventDefinitionsTable.update({ EventDefinitionsTable.id eq event.id }) { row ->
            row[title] = event.title
            row[description] = event.description
            row[defaultPrice] = event.defaultPrice
            row[defaultCapacity] = event.defaultCapacity
            row[defaultDurationMs] = event.defaultDuration.inWholeMilliseconds
            row[allowedPaymentTypes] = event.allowedPaymentTypes
            row[customFields] = event.customFields
            row[recurrenceType] = event.recurrenceType
            row[recurrenceEndDate] = event.recurrenceEndDate
        }
        event
    }

    override suspend fun delete(id: Uuid): Boolean = dbQuery {
        EventDefinitionsTable.deleteWhere { EventDefinitionsTable.id eq id } > 0
    }
}


// --- EVENT SERIES REPOSITORY ---

class ExposedEventSeriesRepository : EventSeriesRepository {

    override suspend fun get(id: Uuid): EventSeries? = dbQuery {
        EventSeriesTable.selectAll()
            .where { EventSeriesTable.id eq id }
            .map { it.toEventSeries() }
            .singleOrNull()
    }

    override suspend fun getAll(seriesIds: List<Uuid>?): List<EventSeries> = dbQuery {
        val query = EventSeriesTable.selectAll()
        if (seriesIds != null) {
            query.where { EventSeriesTable.id inList seriesIds }
        }
        query.map { it.toEventSeries() }
    }

    override suspend fun create(series: EventSeries): EventSeries = dbQuery {
        EventSeriesTable.insert { row ->
            row[id] = series.id
            row[definitionId] = series.definitionId
            row[title] = series.title
            row[description] = series.description
            row[price] = series.price
            row[capacity] = series.capacity
            row[occupiedSpots] = series.occupiedSpots
            row[startDate] = series.startDate
            row[endDate] = series.endDate
            row[lessonCount] = series.lessonCount
            row[allowedPaymentTypes] = series.allowedPaymentTypes
            row[customFields] = series.customFields
        }
        series
    }

    override suspend fun update(series: EventSeries): EventSeries = dbQuery {
        EventDefinitionsTable.update({ EventSeriesTable.id eq series.id }) { row ->
            row[title] = series.title
            row[description] = series.description
            row[defaultPrice] = series.price
            row[defaultCapacity] = series.capacity
            row[allowedPaymentTypes] = series.allowedPaymentTypes
            row[customFields] = series.customFields
        }
        series
    }

    override suspend fun delete(id: Uuid): Boolean = dbQuery {
        EventSeriesTable.deleteWhere { EventSeriesTable.id eq id } > 0
    }

    override suspend fun attemptToReserveSpots(seriesId: Uuid, amount: Int): Boolean = dbQuery {
        val updatedRows = EventSeriesTable.update({
            (EventSeriesTable.id eq seriesId) and
                    (EventSeriesTable.occupiedSpots.plus(amount) lessEq EventSeriesTable.capacity)
        }) {
            it.update(EventSeriesTable.occupiedSpots, EventSeriesTable.occupiedSpots + amount)
        }
        updatedRows > 0
    }

    override suspend fun incrementOccupiedSpots(seriesId: Uuid, amount: Int): Int? = dbQuery {
        EventSeriesTable.update({ EventSeriesTable.id eq seriesId }) {
            it.update(EventSeriesTable.occupiedSpots, EventSeriesTable.occupiedSpots + amount)
        }
        get(seriesId)?.occupiedSpots
    }

    override suspend fun decrementOccupiedSpots(seriesId: Uuid, amount: Int): Int? = dbQuery {
        EventSeriesTable.update({ EventSeriesTable.id eq seriesId }) {
            it.update(EventSeriesTable.occupiedSpots, EventSeriesTable.occupiedSpots - amount)
        }
        get(seriesId)?.occupiedSpots
    }
}


// --- EVENT INSTANCE REPOSITORY ---

class ExposedEventInstanceRepository : EventInstanceRepository {

    override suspend fun get(id: Uuid): EventInstance? = dbQuery {
        EventInstancesTable.selectAll()
            .where { EventInstancesTable.id eq id }
            .map { it.toEventInstance() }
            .singleOrNull()
    }

    override suspend fun getAll(eventIds: List<Uuid>?): List<EventInstance> = dbQuery {
        val query = EventInstancesTable.selectAll()
        if (eventIds != null) {
            query.where { EventInstancesTable.id inList eventIds }
        }
        query.map { it.toEventInstance() }
    }

    override suspend fun create(instance: EventInstance): EventInstance = dbQuery {
        EventInstancesTable.insert { row ->
            row[id] = instance.id
            row[definitionId] = instance.definitionId
            row[seriesId] = instance.seriesId
            row[title] = instance.title
            row[description] = instance.description
            row[startDateTime] = instance.startDateTime
            row[endDateTime] = instance.endDateTime
            row[price] = instance.price
            row[capacity] = instance.capacity
            row[occupiedSpots] = instance.occupiedSpots
            row[isCancelled] = instance.isCancelled
            row[allowedPaymentTypes] = instance.allowedPaymentTypes
            row[customFields] = instance.customFields
        }
        instance
    }

    override suspend fun update(instance: EventInstance): EventInstance = dbQuery {
        EventInstancesTable.update({ EventInstancesTable.id eq instance.id }) { row ->
            row[definitionId] = instance.definitionId
            row[seriesId] = instance.seriesId
            row[title] = instance.title
            row[description] = instance.description
            row[startDateTime] = instance.startDateTime
            row[endDateTime] = instance.endDateTime
            row[price] = instance.price
            row[capacity] = instance.capacity
            row[occupiedSpots] = instance.occupiedSpots
            row[isCancelled] = instance.isCancelled
            row[allowedPaymentTypes] = instance.allowedPaymentTypes
            row[customFields] = instance.customFields
        }
        instance
    }

    override suspend fun delete(id: Uuid): Boolean = dbQuery {
        EventInstancesTable.deleteWhere { EventInstancesTable.id eq id } > 0
    }

    override suspend fun deleteAllByDefinitionId(definitionId: Uuid): Unit = dbQuery {
        EventInstancesTable.deleteWhere { EventInstancesTable.definitionId eq definitionId }
    }

    override suspend fun findByDateRange(from: LocalDateTime, to: LocalDateTime): List<EventInstance> = dbQuery {
        EventInstancesTable.selectAll()
            .where { (EventInstancesTable.startDateTime greaterEq from) and (EventInstancesTable.startDateTime lessEq to) }
            .map { it.toEventInstance() }
    }

    override suspend fun attemptToReserveSpots(instanceId: Uuid, amount: Int): Boolean = dbQuery {
        val updatedRows = EventInstancesTable.update({
            (EventInstancesTable.id eq instanceId) and
                    (EventInstancesTable.occupiedSpots.plus(amount) lessEq EventInstancesTable.capacity)
        }) {
            it.update(EventInstancesTable.occupiedSpots, EventInstancesTable.occupiedSpots + amount)
        }
        updatedRows > 0
    }

    override suspend fun incrementOccupiedSpots(instanceId: Uuid, amount: Int): Int? = dbQuery {
        EventInstancesTable.update({ EventInstancesTable.id eq instanceId }) {
            it.update(EventInstancesTable.occupiedSpots, EventInstancesTable.occupiedSpots + amount)
        }
        get(instanceId)?.occupiedSpots
    }

    override suspend fun decrementOccupiedSpots(instanceId: Uuid, amount: Int): Int? = dbQuery {
        EventInstancesTable.update({ EventInstancesTable.id eq instanceId }) {
            it.update(EventInstancesTable.occupiedSpots, EventInstancesTable.occupiedSpots - amount)
        }
        get(instanceId)?.occupiedSpots
    }
}