package cz.svitaninymburk.projects.reservations.slot.repository

import cz.svitaninymburk.projects.reservations.slot.AvailabilityException
import cz.svitaninymburk.projects.reservations.slot.AvailabilityRule
import cz.svitaninymburk.projects.reservations.slot.SlotService
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

interface SlotServiceRepository {
    suspend fun get(id: Uuid): SlotService?
    /** Seřazené podle názvu. */
    suspend fun getAll(): List<SlotService>
    suspend fun getAllPublished(): List<SlotService>
    suspend fun create(service: SlotService): SlotService
    suspend fun update(service: SlotService): SlotService
    /** Smaže službu i s jejími pravidly a výjimkami. */
    suspend fun delete(id: Uuid): Boolean
}

interface AvailabilityRuleRepository {
    /** Seřazená podle dne v týdnu a začátku. */
    suspend fun findByService(serviceId: Uuid): List<AvailabilityRule>

    /**
     * Nahradí celý rozvrh služby najednou: admin ukládá týden jako celek,
     * takže nejde o jednotlivá pravidla, ale o celou sadu.
     */
    suspend fun replaceForService(serviceId: Uuid, rules: List<AvailabilityRule>): List<AvailabilityRule>
}

interface AvailabilityExceptionRepository {
    /** Výjimky služby v rozsahu [from]–[to] (včetně, null = bez omezení), seřazené podle data. */
    suspend fun findByService(serviceId: Uuid, from: LocalDate? = null, to: LocalDate? = null): List<AvailabilityException>
    /** Založí, nebo podle id přepíše. */
    suspend fun save(exception: AvailabilityException): AvailabilityException
    suspend fun delete(id: Uuid): Boolean
}
