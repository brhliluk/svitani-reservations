package cz.svitaninymburk.projects.reservations.slot.repository

import cz.svitaninymburk.projects.reservations.slot.AvailabilityException
import cz.svitaninymburk.projects.reservations.slot.AvailabilityRule
import cz.svitaninymburk.projects.reservations.slot.SlotService
import kotlinx.datetime.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * Pravidla a výjimky jsou v jednom úložišti se službami, aby smazání služby
 * smazalo i je, stejně jako v databázi.
 */
class InMemorySlotStore {
    internal val services = ConcurrentHashMap<Uuid, SlotService>()
    internal val rules = ConcurrentHashMap<Uuid, AvailabilityRule>()
    internal val exceptions = ConcurrentHashMap<Uuid, AvailabilityException>()
}

class InMemorySlotServiceRepository(private val store: InMemorySlotStore) : SlotServiceRepository {
    override suspend fun get(id: Uuid): SlotService? = store.services[id]

    override suspend fun getAll(): List<SlotService> = store.services.values.sortedBy { it.title }

    override suspend fun getAllPublished(): List<SlotService> = getAll().filter { it.isPublished }

    override suspend fun create(service: SlotService): SlotService {
        store.services[service.id] = service
        return service
    }

    override suspend fun update(service: SlotService): SlotService {
        store.services[service.id] = service
        return service
    }

    override suspend fun delete(id: Uuid): Boolean {
        store.rules.values.removeIf { it.serviceId == id }
        store.exceptions.values.removeIf { it.serviceId == id }
        return store.services.remove(id) != null
    }
}

class InMemoryAvailabilityRuleRepository(private val store: InMemorySlotStore) : AvailabilityRuleRepository {
    override suspend fun findByService(serviceId: Uuid): List<AvailabilityRule> =
        store.rules.values
            .filter { it.serviceId == serviceId }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.from }))

    override suspend fun replaceForService(serviceId: Uuid, rules: List<AvailabilityRule>): List<AvailabilityRule> {
        store.rules.values.removeIf { it.serviceId == serviceId }
        rules.forEach { store.rules[it.id] = it.copy(serviceId = serviceId) }
        return findByService(serviceId)
    }
}

class InMemoryAvailabilityExceptionRepository(private val store: InMemorySlotStore) : AvailabilityExceptionRepository {
    override suspend fun findByService(serviceId: Uuid, from: LocalDate?, to: LocalDate?): List<AvailabilityException> =
        store.exceptions.values
            .filter { it.serviceId == serviceId && (from == null || it.date >= from) && (to == null || it.date <= to) }
            .sortedBy { it.date }

    override suspend fun save(exception: AvailabilityException): AvailabilityException {
        store.exceptions[exception.id] = exception
        return exception
    }

    override suspend fun delete(id: Uuid): Boolean = store.exceptions.remove(id) != null
}
