package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * Nový termín ze šablony: náhradníky, platby a e-maily lektorů, které admin
 * vyplnil ve formuláři, server dřív zahodil a termín dostal výchozí hodnoty.
 */
class TemplateInstanceCreateSpec {
    private val defRepo = InMemoryEventDefinitionRepository()
    private val instanceRepo = InMemoryEventInstanceRepository()
    private val service = AuthenticatedEventService(defRepo, instanceRepo)

    private val definition = EventDefinition(
        id = Uuid.random(),
        title = "Šablona",
        description = "",
        defaultPrice = 100.0,
        defaultCapacity = 10,
        defaultDuration = 1.hours,
        allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
    )

    private fun create(request: CreateEventInstanceRequest) = runBlocking {
        defRepo.create(definition)
        service.createEventInstance(request).getOrNull()!!
        instanceRepo.getAll(null).single()
    }

    @Test
    fun `hodnoty z formulare se ulozi`() {
        val instance = create(
            CreateEventInstanceRequest(
                definitionId = definition.id,
                startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
                waitlistCapacity = 0,
                allowedPaymentTypes = listOf(PaymentType.ON_SITE),
                ownerEmails = listOf(" lektorka@svitani.cz "),
            )
        )

        assertEquals(0, instance.waitlistCapacity)
        assertEquals(listOf(PaymentType.ON_SITE), instance.allowedPaymentTypes)
        assertEquals(listOf("lektorka@svitani.cz"), instance.ownerEmails)
    }

    @Test
    fun `bez zvolene platby plati ta ze sablony`() {
        val instance = create(
            CreateEventInstanceRequest(
                definitionId = definition.id,
                startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
                allowedPaymentTypes = emptyList(),
            )
        )

        assertEquals(listOf(PaymentType.BANK_TRANSFER), instance.allowedPaymentTypes)
    }
}
