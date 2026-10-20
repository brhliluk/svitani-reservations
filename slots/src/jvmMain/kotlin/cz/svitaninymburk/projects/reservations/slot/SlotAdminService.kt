package cz.svitaninymburk.projects.reservations.slot

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.AuditService
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityExceptionRepository
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityRuleRepository
import cz.svitaninymburk.projects.reservations.slot.repository.SlotServiceRepository
import cz.svitaninymburk.projects.reservations.util.APP_TIMEZONE
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Stornuje rezervace na mazanou službu stejně, jako jádro ruší rezervace na smazanou
 * akci (oznámení, volitelná vratka). V běhu je to `AdminDashboardService.cancelReservationsOfDeletedTarget`.
 */
fun interface DeletedTargetCanceller {
    suspend fun cancel(references: Collection<Reference>, noticeTitle: String, refund: Boolean)
}

/**
 * Správa hlídání pro admina. Rozvrh ani nastavení nikdy samy rezervace neruší —
 * jen je ukážou ([previewSchedule]); rušení je výslovná akce (smazání služby).
 */
class SlotAdminService(
    private val services: SlotServiceRepository,
    private val rules: AvailabilityRuleRepository,
    private val exceptions: AvailabilityExceptionRepository,
    private val availability: SlotAvailability,
    private val reservations: ReservationRepository,
    private val audit: AuditService,
    private val canceller: DeletedTargetCanceller,
    private val clock: Clock = Clock.System,
) : SlotAdminServiceInterface {

    private fun now(): LocalDateTime = clock.now().toLocalDateTime(APP_TIMEZONE)

    override suspend fun listServices(): List<SlotService> = services.getAll()

    override suspend fun getService(id: Uuid): Either<SlotAdminError, SlotServiceDetail> = either {
        val service = ensureNotNull(services.get(id)) { SlotAdminError.ServiceNotFound }
        SlotServiceDetail(
            service = service,
            rules = rules.findByService(id),
            exceptions = exceptions.findByService(id),
            upcomingReservationCount = upcomingReservations(id).size,
        )
    }

    override suspend fun createService(service: SlotService): Either<SlotAdminError, Uuid> = either {
        val normalized = service.normalized().copy(id = Uuid.random())
        val problems = validateSlotService(normalized)
        ensure(problems.isEmpty()) { SlotAdminError.InvalidService(problems) }
        services.create(normalized)
        audit.record(
            type = AuditEventType.EVENT_CREATED,
            subjectLabel = normalized.title,
            detail = "Hlídání: založena služba ${normalized.title}",
        )
        normalized.id
    }

    override suspend fun updateService(service: SlotService): Either<SlotAdminError, Unit> = either {
        val stored = ensureNotNull(services.get(service.id)) { SlotAdminError.ServiceNotFound }
        // Publikace má vlastní akci (a vlastní záznam v historii), formulář ji nemění.
        val normalized = service.normalized().copy(isPublished = stored.isPublished)
        val problems = validateSlotService(normalized)
        ensure(problems.isEmpty()) { SlotAdminError.InvalidService(problems) }
        services.update(normalized)
        audit.record(
            type = AuditEventType.EVENT_UPDATED,
            subjectLabel = normalized.title,
            detail = "Hlídání: upraveno nastavení služby",
        )
    }

    override suspend fun setPublished(id: Uuid, published: Boolean): Either<SlotAdminError, Unit> = either {
        val stored = ensureNotNull(services.get(id)) { SlotAdminError.ServiceNotFound }
        if (stored.isPublished == published) return@either
        services.update(stored.copy(isPublished = published))
        audit.record(
            type = if (published) AuditEventType.EVENT_PUBLISHED else AuditEventType.EVENT_UNPUBLISHED,
            subjectLabel = stored.title,
            detail = if (published) "Hlídání: služba zveřejněna" else "Hlídání: služba skryta",
        )
    }

    override suspend fun deleteService(id: Uuid, refund: Boolean): Either<SlotAdminError, Unit> = either {
        val stored = ensureNotNull(services.get(id)) { SlotAdminError.ServiceNotFound }
        val active = reservations.findActiveSlotReservations(id, EARLIEST, LATEST)

        // Ještě před smazáním — pak už nebude z čeho vzít název (stejně jako u akcí).
        audit.record(
            type = AuditEventType.EVENT_DELETED,
            subjectLabel = stored.title,
            detail = "Hlídání: smazána služba ${stored.title}, zrušeno rezervací: ${active.size}" +
                if (refund) ", kredit vracen" else ", bez vracení kreditu",
        )
        canceller.cancel(active.map { it.reference }, stored.title, refund)
        services.delete(id)
    }

    override suspend fun previewSchedule(
        id: Uuid,
        rules: List<AvailabilityRule>,
        exceptions: List<AvailabilityException>,
    ): Either<SlotAdminError, List<SlotReservationRow>> = either {
        val service = ensureNotNull(services.get(id)) { SlotAdminError.ServiceNotFound }
        val newRules = rules.map { it.copy(serviceId = id) }
        val newExceptions = exceptions.map { it.copy(serviceId = id) }
        val problems = validateSchedule(newRules, newExceptions)
        ensure(problems.isEmpty()) { SlotAdminError.InvalidSchedule(problems) }

        upcomingReservations(id).filter { reservation ->
            val slot = reservation.reference as? Reference.Slot ?: return@filter false
            val day = expandAvailability(
                service = service,
                rules = newRules,
                exceptions = newExceptions.filter { it.date == slot.start.date },
                bookedIntervals = emptyList(),
                from = slot.start.date,
                to = slot.start.date,
            ).firstOrNull()
            stepsCovering(day, slot.start, slot.end) == null
        }.map { it.toRow() }
    }

    override suspend fun saveSchedule(
        id: Uuid,
        rules: List<AvailabilityRule>,
        exceptions: List<AvailabilityException>,
    ): Either<SlotAdminError, Unit> = either {
        val service = ensureNotNull(services.get(id)) { SlotAdminError.ServiceNotFound }
        val newRules = rules.map { it.copy(id = Uuid.random(), serviceId = id) }
        val newExceptions = exceptions.map { it.copy(serviceId = id) }
        val problems = validateSchedule(newRules, newExceptions)
        ensure(problems.isEmpty()) { SlotAdminError.InvalidSchedule(problems) }

        this@SlotAdminService.rules.replaceForService(id, newRules)
        val keptIds = newExceptions.map { it.id }.toSet()
        this@SlotAdminService.exceptions.findByService(id)
            .filter { it.id !in keptIds }
            .forEach { this@SlotAdminService.exceptions.delete(it.id) }
        newExceptions.forEach { this@SlotAdminService.exceptions.save(it) }

        audit.record(
            type = AuditEventType.EVENT_UPDATED,
            subjectLabel = service.title,
            detail = "Hlídání: upraven rozvrh (oken: ${newRules.size}, výjimek: ${newExceptions.size})",
        )
    }

    override suspend fun getDayOverview(id: Uuid, date: LocalDate): Either<SlotAdminError, SlotDayOverview> = either {
        val service = ensureNotNull(services.get(id)) { SlotAdminError.ServiceNotFound }
        val segments = availability.days(service, date, date).firstOrNull()?.segments.orEmpty()
        val dayReservations = reservations
            .findActiveSlotReservations(id, date.atStartOfDay(), date.plus(1, DateTimeUnit.DAY).atStartOfDay())
            .map { it.toRow() }
            .sortedWith(compareBy({ it.start }, { it.contactName }))
        SlotDayOverview(date, segments, dayReservations)
    }

    /** Aktivní rezervace, které ještě neskončily. */
    private suspend fun upcomingReservations(serviceId: Uuid): List<Reservation> =
        reservations.findActiveSlotReservations(serviceId, now(), LATEST)

    private companion object {
        val EARLIEST = LocalDateTime(1970, 1, 1, 0, 0)
        val LATEST = LocalDateTime(9999, 12, 31, 0, 0)
    }
}

/** Ořezané texty a e-maily bez prázdných řádků z formuláře. */
private fun SlotService.normalized(): SlotService = copy(
    title = title.trim(),
    description = description.trim(),
    ownerEmails = ownerEmails.map { it.trim() }.filter { it.isNotEmpty() },
)

private fun Reservation.toRow(): SlotReservationRow {
    val slot = reference as Reference.Slot
    return SlotReservationRow(
        id = id,
        start = slot.start,
        end = slot.end,
        contactName = contactName,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        seatCount = seatCount,
        status = status,
        paymentType = paymentType,
        totalPrice = totalPrice,
        variableSymbol = variableSymbol,
        customValues = customValues,
    )
}
