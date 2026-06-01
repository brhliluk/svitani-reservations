package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.util.dbQuery
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.json.json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

// --- 0. OWNER EMAILS ---
object EventOwnerEmailsTable : Table("event_owner_emails") {
    val entityType = varchar("entity_type", 20)
    val entityId = uuid("entity_id")
    val email = varchar("email", 255)
}

object EntityType {
    const val DEFINITION = "definition"
    const val SERIES = "series"
    const val INSTANCE = "instance"
}

private fun getOwnerEmails(entityType: String, entityId: Uuid): List<String> =
    EventOwnerEmailsTable.selectAll()
        .where {
            (EventOwnerEmailsTable.entityType eq entityType) and
            (EventOwnerEmailsTable.entityId eq entityId)
        }
        .map { it[EventOwnerEmailsTable.email] }

private fun getOwnerEmailsMap(entityType: String, entityIds: Collection<Uuid>): Map<Uuid, List<String>> {
    if (entityIds.isEmpty()) return emptyMap()
    return EventOwnerEmailsTable.selectAll()
        .where {
            (EventOwnerEmailsTable.entityType eq entityType) and
            (EventOwnerEmailsTable.entityId inList entityIds.toList())
        }
        .groupBy { it[EventOwnerEmailsTable.entityId] }
        .mapValues { (_, rows) -> rows.map { it[EventOwnerEmailsTable.email] } }
}

private fun setOwnerEmails(entityType: String, entityId: Uuid, emails: List<String>) {
    EventOwnerEmailsTable.deleteWhere {
        (EventOwnerEmailsTable.entityType eq entityType) and
        (EventOwnerEmailsTable.entityId eq entityId)
    }
    emails.filter { it.isNotBlank() }.forEach { email ->
        EventOwnerEmailsTable.insert {
            it[EventOwnerEmailsTable.entityType] = entityType
            it[EventOwnerEmailsTable.entityId] = entityId
            it[EventOwnerEmailsTable.email] = email
        }
    }
}

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
    val showAttendeeCount = bool("show_attendee_count").default(true)

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toEventDefinition(ownerEmails: List<String>): EventDefinition = EventDefinition(
    id = this[EventDefinitionsTable.id],
    title = this[EventDefinitionsTable.title],
    description = this[EventDefinitionsTable.description],
    defaultPrice = this[EventDefinitionsTable.defaultPrice],
    defaultCapacity = this[EventDefinitionsTable.defaultCapacity],
    defaultDuration = this[EventDefinitionsTable.defaultDurationMs].milliseconds,
    allowedPaymentTypes = this[EventDefinitionsTable.allowedPaymentTypes],
    customFields = this[EventDefinitionsTable.customFields],
    ownerEmails = ownerEmails,
    showAttendeeCount = this[EventDefinitionsTable.showAttendeeCount],
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
    val lessonDayOfWeek = integer("lesson_day_of_week").nullable()
    val lessonStartTime = varchar("lesson_start_time", 8).nullable()
    val lessonEndTime = varchar("lesson_end_time", 8).nullable()
    val showAttendeeCount = bool("show_attendee_count").default(true)

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toEventSeries(ownerEmails: List<String>): EventSeries = EventSeries(
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
    customFields = this[EventSeriesTable.customFields],
    ownerEmails = ownerEmails,
    lessonDayOfWeek = this[EventSeriesTable.lessonDayOfWeek]?.let { DayOfWeek(it) },
    lessonStartTime = this[EventSeriesTable.lessonStartTime]?.let { LocalTime.parse(it) },
    lessonEndTime = this[EventSeriesTable.lessonEndTime]?.let { LocalTime.parse(it) },
    showAttendeeCount = this[EventSeriesTable.showAttendeeCount],
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
    val isDropIn = bool("is_drop_in").default(false)
    val showAttendeeCount = bool("show_attendee_count").default(true)

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toEventInstance(ownerEmails: List<String>): EventInstance = EventInstance(
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
    customFields = this[EventInstancesTable.customFields],
    ownerEmails = ownerEmails,
    isDropIn = this[EventInstancesTable.isDropIn],
    showAttendeeCount = this[EventInstancesTable.showAttendeeCount],
)

class ExposedEventDefinitionRepository : EventDefinitionRepository {

    override suspend fun get(id: Uuid): EventDefinition? = dbQuery {
        val row = EventDefinitionsTable.selectAll()
            .where { EventDefinitionsTable.id eq id }
            .singleOrNull() ?: return@dbQuery null
        val emails = getOwnerEmails(EntityType.DEFINITION, id)
        row.toEventDefinition(emails)
    }

    override suspend fun getAll(definitionIds: List<Uuid>?): List<EventDefinition> = dbQuery {
        val query = EventDefinitionsTable.selectAll()
        if (definitionIds != null) {
            query.where { EventDefinitionsTable.id inList definitionIds }
        }
        val rows = query.toList()
        val ids = rows.map { it[EventDefinitionsTable.id] }
        val emailsMap = getOwnerEmailsMap(EntityType.DEFINITION, ids)
        rows.map { it.toEventDefinition(emailsMap[it[EventDefinitionsTable.id]] ?: emptyList()) }
    }

    override suspend fun findAllPaged(page: Int, pageSize: Int): List<EventDefinition> = dbQuery {
        val rows = EventDefinitionsTable.selectAll()
            .orderBy(EventDefinitionsTable.title, SortOrder.ASC)
            .limit(pageSize)
            .offset(page.toLong() * pageSize)
            .toList()
        val ids = rows.map { it[EventDefinitionsTable.id] }
        val emailsMap = getOwnerEmailsMap(EntityType.DEFINITION, ids)
        rows.map { it.toEventDefinition(emailsMap[it[EventDefinitionsTable.id]] ?: emptyList()) }
    }

    override suspend fun countAll(): Long = dbQuery {
        EventDefinitionsTable.selectAll().count()
    }

    override suspend fun create(event: EventDefinition): EventDefinition = dbQuery {
        EventDefinitionsTable.insert { row ->
            row[id] = event.id
            row[title] = event.title
            row[description] = event.description
            row[defaultPrice] = event.defaultPrice
            row[defaultCapacity] = event.defaultCapacity
            row[defaultDurationMs] = event.defaultDuration.inWholeMilliseconds
            row[allowedPaymentTypes] = event.allowedPaymentTypes
            row[customFields] = event.customFields
            row[showAttendeeCount] = event.showAttendeeCount
        }
        setOwnerEmails(EntityType.DEFINITION, event.id, event.ownerEmails)
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
            row[showAttendeeCount] = event.showAttendeeCount
        }
        setOwnerEmails(EntityType.DEFINITION, event.id, event.ownerEmails)
        event
    }

    override suspend fun delete(id: Uuid): Boolean = dbQuery {
        val seriesIds = EventSeriesTable.selectAll()
            .where { EventSeriesTable.definitionId eq id }
            .map { it[EventSeriesTable.id] }
        val instanceIds = EventInstancesTable.selectAll()
            .where { EventInstancesTable.definitionId eq id }
            .map { it[EventInstancesTable.id] }

        EventOwnerEmailsTable.deleteWhere {
            (EventOwnerEmailsTable.entityType eq EntityType.DEFINITION) and
            (EventOwnerEmailsTable.entityId eq id)
        }
        if (seriesIds.isNotEmpty()) {
            EventOwnerEmailsTable.deleteWhere {
                (EventOwnerEmailsTable.entityType eq EntityType.SERIES) and
                (EventOwnerEmailsTable.entityId inList seriesIds)
            }
        }
        if (instanceIds.isNotEmpty()) {
            EventOwnerEmailsTable.deleteWhere {
                (EventOwnerEmailsTable.entityType eq EntityType.INSTANCE) and
                (EventOwnerEmailsTable.entityId inList instanceIds)
            }
        }
        EventDefinitionsTable.deleteWhere { EventDefinitionsTable.id eq id } > 0
    }
}


// --- EVENT SERIES REPOSITORY ---

class ExposedEventSeriesRepository : EventSeriesRepository {

    override suspend fun get(id: Uuid): EventSeries? = dbQuery {
        val row = EventSeriesTable.selectAll()
            .where { EventSeriesTable.id eq id }
            .singleOrNull() ?: return@dbQuery null
        val emails = getOwnerEmails(EntityType.SERIES, id)
        row.toEventSeries(emails)
    }

    override suspend fun getAll(seriesIds: List<Uuid>?): List<EventSeries> = dbQuery {
        val query = EventSeriesTable.selectAll()
        if (seriesIds != null) {
            query.where { EventSeriesTable.id inList seriesIds }
        }
        val rows = query.toList()
        val ids = rows.map { it[EventSeriesTable.id] }
        val emailsMap = getOwnerEmailsMap(EntityType.SERIES, ids)
        rows.map { it.toEventSeries(emailsMap[it[EventSeriesTable.id]] ?: emptyList()) }
    }

    override suspend fun getAllByDefinitionIds(definitionIds: List<Uuid>): List<EventSeries> {
        if (definitionIds.isEmpty()) return emptyList()
        return dbQuery {
            val rows = EventSeriesTable.selectAll()
                .where { EventSeriesTable.definitionId inList definitionIds }
                .toList()
            val ids = rows.map { it[EventSeriesTable.id] }
            val emailsMap = getOwnerEmailsMap(EntityType.SERIES, ids)
            rows.map { it.toEventSeries(emailsMap[it[EventSeriesTable.id]] ?: emptyList()) }
        }
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
            row[lessonDayOfWeek] = series.lessonDayOfWeek?.isoDayNumber
            row[lessonStartTime] = series.lessonStartTime?.toString()
            row[lessonEndTime] = series.lessonEndTime?.toString()
            row[showAttendeeCount] = series.showAttendeeCount
        }
        setOwnerEmails(EntityType.SERIES, series.id, series.ownerEmails)
        series
    }

    override suspend fun update(series: EventSeries): EventSeries = dbQuery {
        EventSeriesTable.update({ EventSeriesTable.id eq series.id }) { row ->
            row[title] = series.title
            row[description] = series.description
            row[price] = series.price
            row[capacity] = series.capacity
            row[startDate] = series.startDate
            row[endDate] = series.endDate
            row[lessonCount] = series.lessonCount
            row[allowedPaymentTypes] = series.allowedPaymentTypes
            row[customFields] = series.customFields
            row[lessonDayOfWeek] = series.lessonDayOfWeek?.isoDayNumber
            row[lessonStartTime] = series.lessonStartTime?.toString()
            row[lessonEndTime] = series.lessonEndTime?.toString()
            row[showAttendeeCount] = series.showAttendeeCount
        }
        setOwnerEmails(EntityType.SERIES, series.id, series.ownerEmails)
        series
    }

    override suspend fun delete(id: Uuid): Boolean = dbQuery {
        val instanceIds = EventInstancesTable.selectAll()
            .where { EventInstancesTable.seriesId eq id }
            .map { it[EventInstancesTable.id] }
        if (instanceIds.isNotEmpty()) {
            EventOwnerEmailsTable.deleteWhere {
                (EventOwnerEmailsTable.entityType eq EntityType.INSTANCE) and
                (EventOwnerEmailsTable.entityId inList instanceIds)
            }
        }
        EventOwnerEmailsTable.deleteWhere {
            (EventOwnerEmailsTable.entityType eq EntityType.SERIES) and
            (EventOwnerEmailsTable.entityId eq id)
        }
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
        val row = EventInstancesTable.selectAll()
            .where { EventInstancesTable.id eq id }
            .singleOrNull() ?: return@dbQuery null
        val emails = getOwnerEmails(EntityType.INSTANCE, id)
        row.toEventInstance(emails)
    }

    override suspend fun getAll(eventIds: List<Uuid>?): List<EventInstance> = dbQuery {
        val query = EventInstancesTable.selectAll()
        if (eventIds != null) {
            query.where { EventInstancesTable.id inList eventIds }
        }
        val rows = query.toList()
        val ids = rows.map { it[EventInstancesTable.id] }
        val emailsMap = getOwnerEmailsMap(EntityType.INSTANCE, ids)
        rows.map { it.toEventInstance(emailsMap[it[EventInstancesTable.id]] ?: emptyList()) }
    }

    override suspend fun getAllByDefinitionIds(definitionIds: List<Uuid>): List<EventInstance> {
        if (definitionIds.isEmpty()) return emptyList()
        return dbQuery {
            val rows = EventInstancesTable.selectAll()
                .where { EventInstancesTable.definitionId inList definitionIds }
                .toList()
            val ids = rows.map { it[EventInstancesTable.id] }
            val emailsMap = getOwnerEmailsMap(EntityType.INSTANCE, ids)
            rows.map { it.toEventInstance(emailsMap[it[EventInstancesTable.id]] ?: emptyList()) }
        }
    }

    override suspend fun findBySeriesPaged(seriesId: Uuid, page: Int, pageSize: Int): List<EventInstance> = dbQuery {
        val rows = EventInstancesTable.selectAll()
            .where { EventInstancesTable.seriesId eq seriesId }
            .orderBy(EventInstancesTable.startDateTime, SortOrder.ASC)
            .limit(pageSize)
            .offset(page.toLong() * pageSize)
            .toList()
        val ids = rows.map { it[EventInstancesTable.id] }
        val emailsMap = getOwnerEmailsMap(EntityType.INSTANCE, ids)
        rows.map { it.toEventInstance(emailsMap[it[EventInstancesTable.id]] ?: emptyList()) }
    }

    override suspend fun findBySeries(seriesId: Uuid): List<EventInstance> = dbQuery {
        val rows = EventInstancesTable.selectAll()
            .where { EventInstancesTable.seriesId eq seriesId }
            .orderBy(EventInstancesTable.startDateTime, SortOrder.ASC)
            .toList()
        val ids = rows.map { it[EventInstancesTable.id] }
        val emailsMap = getOwnerEmailsMap(EntityType.INSTANCE, ids)
        rows.map { it.toEventInstance(emailsMap[it[EventInstancesTable.id]] ?: emptyList()) }
    }

    override suspend fun countBySeries(seriesId: Uuid): Long = dbQuery {
        EventInstancesTable.selectAll()
            .where { EventInstancesTable.seriesId eq seriesId }
            .count()
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
            row[isDropIn] = instance.isDropIn
            row[showAttendeeCount] = instance.showAttendeeCount
        }
        setOwnerEmails(EntityType.INSTANCE, instance.id, instance.ownerEmails)
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
            row[isDropIn] = instance.isDropIn
            row[showAttendeeCount] = instance.showAttendeeCount
        }
        setOwnerEmails(EntityType.INSTANCE, instance.id, instance.ownerEmails)
        instance
    }

    override suspend fun delete(id: Uuid): Boolean = dbQuery {
        EventOwnerEmailsTable.deleteWhere {
            (EventOwnerEmailsTable.entityType eq EntityType.INSTANCE) and
            (EventOwnerEmailsTable.entityId eq id)
        }
        EventInstancesTable.deleteWhere { EventInstancesTable.id eq id } > 0
    }

    override suspend fun deleteAllByDefinitionId(definitionId: Uuid): Unit = dbQuery {
        val ids = EventInstancesTable.selectAll()
            .where { EventInstancesTable.definitionId eq definitionId }
            .map { it[EventInstancesTable.id] }
        if (ids.isNotEmpty()) {
            EventOwnerEmailsTable.deleteWhere {
                (EventOwnerEmailsTable.entityType eq EntityType.INSTANCE) and
                (EventOwnerEmailsTable.entityId inList ids)
            }
        }
        EventInstancesTable.deleteWhere { EventInstancesTable.definitionId eq definitionId }
    }

    override suspend fun findByDateRange(from: LocalDateTime, to: LocalDateTime): List<EventInstance> = dbQuery {
        val rows = EventInstancesTable.selectAll()
            .where { (EventInstancesTable.startDateTime greaterEq from) and (EventInstancesTable.startDateTime lessEq to) }
            .toList()
        val ids = rows.map { it[EventInstancesTable.id] }
        val emailsMap = getOwnerEmailsMap(EntityType.INSTANCE, ids)
        rows.map { it.toEventInstance(emailsMap[it[EventInstancesTable.id]] ?: emptyList()) }
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
