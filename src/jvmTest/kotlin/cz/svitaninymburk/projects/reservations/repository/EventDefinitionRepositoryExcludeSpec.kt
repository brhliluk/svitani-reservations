package cz.svitaninymburk.projects.reservations.repository

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class EventDefinitionRepositoryExcludeSpec {

    private fun definition(id: Uuid, title: String) = EventDefinition(
        id = id, title = title, description = "", defaultPrice = 100.0, defaultCapacity = 10,
        defaultDuration = 1.hours, allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
        customFields = emptyList(), ownerEmails = emptyList(),
    )

    @Test
    fun `findAllPaged excludes given ids and countAll matches`() = runBlocking {
        val repo = InMemoryEventDefinitionRepository()
        val keep = Uuid.random()
        val drop = Uuid.random()
        repo.create(definition(keep, "A keep"))
        repo.create(definition(drop, "B drop"))

        val paged = repo.findAllPaged(0, 20, setOf(drop))
        assertEquals(listOf(keep), paged.map { it.id })
        assertEquals(1L, repo.countAll(setOf(drop)))
        assertEquals(2L, repo.countAll(emptySet()))
    }
}
