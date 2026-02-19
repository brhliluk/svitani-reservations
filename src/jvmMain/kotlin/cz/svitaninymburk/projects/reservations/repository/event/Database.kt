package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.json
import kotlin.time.Duration.Companion.milliseconds

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

// TODO: Interface impl.
