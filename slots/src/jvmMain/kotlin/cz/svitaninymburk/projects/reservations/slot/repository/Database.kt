package cz.svitaninymburk.projects.reservations.slot.repository

import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.slot.AvailabilityException
import cz.svitaninymburk.projects.reservations.slot.AvailabilityRule
import cz.svitaninymburk.projects.reservations.slot.SlotService
import cz.svitaninymburk.projects.reservations.util.dbQuery
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.json.json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

// Délky se ukládají v milisekundách a časy dne jako text „HH:MM“, stejně jako u kurzů v jádru.

object SlotServicesTable : Table("slot_services") {
    val id = uuid("id")
    val title = varchar("title", 255)
    val description = text("description")
    val stepMs = long("step_ms")
    val minDurationMs = long("min_duration_ms")
    /** null = bez omezení délky. */
    val maxDurationMs = long("max_duration_ms").nullable()
    val pricePerHour = double("price_per_hour")
    val capacity = integer("capacity")
    val maxSeatsPerBooking = integer("max_seats_per_booking")
    val allowedPaymentTypes = json<List<PaymentType>>("allowed_payment_types", Json)
    val customFields = json<List<CustomFieldDefinition>>("custom_fields", Json)
    val ownerEmails = json<List<String>>("owner_emails", Json)
    val reservationDeadlineMs = long("reservation_deadline_ms").nullable()
    val bookingHorizonMs = long("booking_horizon_ms")
    val isPublished = bool("is_published").default(false)

    override val primaryKey = PrimaryKey(id)
}

object AvailabilityRulesTable : Table("slot_availability_rules") {
    val id = uuid("id")
    val serviceId = reference("service_id", SlotServicesTable.id, onDelete = ReferenceOption.CASCADE)
    /** ISO číslo dne, 1 = pondělí. */
    val dayOfWeek = integer("day_of_week")
    val from = text("from_time")
    val to = text("to_time")
    val validFrom = date("valid_from")
    val validTo = date("valid_to").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AvailabilityExceptionsTable : Table("slot_availability_exceptions") {
    val id = uuid("id")
    val serviceId = reference("service_id", SlotServicesTable.id, onDelete = ReferenceOption.CASCADE)
    val date = date("date")
    val closed = bool("closed")
    val from = text("from_time").nullable()
    val to = text("to_time").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, serviceId, date)
    }
}

/** Všechny tabulky modulu, pro založení a migraci při startu. */
val SLOT_TABLES: List<Table> = listOf(SlotServicesTable, AvailabilityRulesTable, AvailabilityExceptionsTable)

class ExposedSlotServiceRepository : SlotServiceRepository {
    override suspend fun get(id: Uuid): SlotService? = dbQuery {
        SlotServicesTable.selectAll().where { SlotServicesTable.id eq id }.singleOrNull()?.toSlotService()
    }

    override suspend fun getAll(): List<SlotService> = dbQuery {
        SlotServicesTable.selectAll().orderBy(SlotServicesTable.title).map { it.toSlotService() }
    }

    override suspend fun getAllPublished(): List<SlotService> = dbQuery {
        SlotServicesTable.selectAll()
            .where { SlotServicesTable.isPublished eq true }
            .orderBy(SlotServicesTable.title)
            .map { it.toSlotService() }
    }

    override suspend fun create(service: SlotService): SlotService = dbQuery {
        SlotServicesTable.insert {
            it[id] = service.id
            it.fill(service)
        }
        service
    }

    override suspend fun update(service: SlotService): SlotService = dbQuery {
        SlotServicesTable.update({ SlotServicesTable.id eq service.id }) { it.fill(service) }
        service
    }

    // SQLite cizí klíče bez `PRAGMA foreign_keys` nevynucuje, CASCADE na nich proto nestačí.
    override suspend fun delete(id: Uuid): Boolean = dbQuery {
        AvailabilityRulesTable.deleteWhere { serviceId eq id }
        AvailabilityExceptionsTable.deleteWhere { serviceId eq id }
        SlotServicesTable.deleteWhere { SlotServicesTable.id eq id } > 0
    }

    private fun UpdateBuilder<*>.fill(service: SlotService) {
        this[SlotServicesTable.title] = service.title
        this[SlotServicesTable.description] = service.description
        this[SlotServicesTable.stepMs] = service.step.inWholeMilliseconds
        this[SlotServicesTable.minDurationMs] = service.minDuration.inWholeMilliseconds
        this[SlotServicesTable.maxDurationMs] = service.maxDuration?.inWholeMilliseconds
        this[SlotServicesTable.pricePerHour] = service.pricePerHour
        this[SlotServicesTable.capacity] = service.capacity
        this[SlotServicesTable.maxSeatsPerBooking] = service.maxSeatsPerBooking
        this[SlotServicesTable.allowedPaymentTypes] = service.allowedPaymentTypes
        this[SlotServicesTable.customFields] = service.customFields
        this[SlotServicesTable.ownerEmails] = service.ownerEmails
        this[SlotServicesTable.reservationDeadlineMs] = service.reservationDeadline?.inWholeMilliseconds
        this[SlotServicesTable.bookingHorizonMs] = service.bookingHorizon.inWholeMilliseconds
        this[SlotServicesTable.isPublished] = service.isPublished
    }
}

class ExposedAvailabilityRuleRepository : AvailabilityRuleRepository {
    override suspend fun findByService(serviceId: Uuid): List<AvailabilityRule> = dbQuery { rulesOf(serviceId) }

    override suspend fun replaceForService(serviceId: Uuid, rules: List<AvailabilityRule>): List<AvailabilityRule> = dbQuery {
        AvailabilityRulesTable.deleteWhere { AvailabilityRulesTable.serviceId eq serviceId }
        rules.forEach { rule ->
            AvailabilityRulesTable.insert {
                it[id] = rule.id
                it[this.serviceId] = serviceId
                it[dayOfWeek] = rule.dayOfWeek.isoDayNumber
                it[from] = rule.from.toString()
                it[to] = rule.to.toString()
                it[validFrom] = rule.validFrom
                it[validTo] = rule.validTo
            }
        }
        rulesOf(serviceId)
    }

    private fun rulesOf(serviceId: Uuid): List<AvailabilityRule> =
        AvailabilityRulesTable.selectAll()
            .where { AvailabilityRulesTable.serviceId eq serviceId }
            .map { it.toAvailabilityRule() }
            // Řadí se podle LocalTime, ne podle textu ve sloupci.
            .sortedWith(compareBy({ it.dayOfWeek }, { it.from }))
}

class ExposedAvailabilityExceptionRepository : AvailabilityExceptionRepository {
    override suspend fun findByService(serviceId: Uuid, from: LocalDate?, to: LocalDate?): List<AvailabilityException> = dbQuery {
        AvailabilityExceptionsTable.selectAll()
            .where {
                var condition: Op<Boolean> = AvailabilityExceptionsTable.serviceId eq serviceId
                if (from != null) condition = condition and (AvailabilityExceptionsTable.date greaterEq from)
                if (to != null) condition = condition and (AvailabilityExceptionsTable.date lessEq to)
                condition
            }
            .orderBy(AvailabilityExceptionsTable.date)
            .map { it.toAvailabilityException() }
    }

    override suspend fun save(exception: AvailabilityException): AvailabilityException = dbQuery {
        AvailabilityExceptionsTable.upsert {
            it[id] = exception.id
            it[serviceId] = exception.serviceId
            it[date] = exception.date
            it[closed] = exception.closed
            it[from] = exception.from?.toString()
            it[to] = exception.to?.toString()
        }
        exception
    }

    override suspend fun delete(id: Uuid): Boolean = dbQuery {
        AvailabilityExceptionsTable.deleteWhere { AvailabilityExceptionsTable.id eq id } > 0
    }
}

private fun ResultRow.toSlotService() = SlotService(
    id = this[SlotServicesTable.id],
    title = this[SlotServicesTable.title],
    description = this[SlotServicesTable.description],
    step = this[SlotServicesTable.stepMs].milliseconds,
    minDuration = this[SlotServicesTable.minDurationMs].milliseconds,
    maxDuration = this[SlotServicesTable.maxDurationMs]?.milliseconds,
    pricePerHour = this[SlotServicesTable.pricePerHour],
    capacity = this[SlotServicesTable.capacity],
    maxSeatsPerBooking = this[SlotServicesTable.maxSeatsPerBooking],
    allowedPaymentTypes = this[SlotServicesTable.allowedPaymentTypes],
    customFields = this[SlotServicesTable.customFields],
    ownerEmails = this[SlotServicesTable.ownerEmails],
    reservationDeadline = this[SlotServicesTable.reservationDeadlineMs]?.milliseconds,
    bookingHorizon = this[SlotServicesTable.bookingHorizonMs].milliseconds,
    isPublished = this[SlotServicesTable.isPublished],
)

private fun ResultRow.toAvailabilityRule() = AvailabilityRule(
    id = this[AvailabilityRulesTable.id],
    serviceId = this[AvailabilityRulesTable.serviceId],
    dayOfWeek = DayOfWeek(this[AvailabilityRulesTable.dayOfWeek]),
    from = LocalTime.parse(this[AvailabilityRulesTable.from]),
    to = LocalTime.parse(this[AvailabilityRulesTable.to]),
    validFrom = this[AvailabilityRulesTable.validFrom],
    validTo = this[AvailabilityRulesTable.validTo],
)

private fun ResultRow.toAvailabilityException() = AvailabilityException(
    id = this[AvailabilityExceptionsTable.id],
    serviceId = this[AvailabilityExceptionsTable.serviceId],
    date = this[AvailabilityExceptionsTable.date],
    closed = this[AvailabilityExceptionsTable.closed],
    from = this[AvailabilityExceptionsTable.from]?.let(LocalTime::parse),
    to = this[AvailabilityExceptionsTable.to]?.let(LocalTime::parse),
)
