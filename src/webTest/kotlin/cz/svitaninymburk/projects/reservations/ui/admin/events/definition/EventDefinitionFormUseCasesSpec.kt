package cz.svitaninymburk.projects.reservations.ui.admin.events.definition

import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.DefinitionFormValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.EventDefinitionFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.buildAllowedPaymentTypes
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.buildCreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.buildUpdateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.validateDefinitionForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private fun sampleForm() = EventDefinitionFormData(
    title = "Jóga",
    description = "Popis",
    ownerEmails = listOf("a@x.cz", "not-an-email"),
    price = 150.0,
    capacity = 12,
    durationHours = 1,
    durationMinutes = 30,
    allowBankTransfer = true,
    allowOnSite = false,
    customFields = emptyList(),
    showAttendeeCount = true,
    allowMultipleSeats = true,
)

class EventDefinitionFormUseCasesSpec {

    @Test
    fun validateDefinitionFormRequiresTitle() {
        assertEquals(DefinitionFormValidationError.MissingTitle, validateDefinitionForm("  ", listOf("a@x.cz")))
    }

    @Test
    fun validateDefinitionFormRequiresAtLeastOneValidOwnerEmail() {
        assertEquals(DefinitionFormValidationError.MissingOwnerEmail, validateDefinitionForm("Jóga", listOf("not-an-email")))
    }

    @Test
    fun validateDefinitionFormPassesWithTitleAndOwnerEmail() {
        assertNull(validateDefinitionForm("Jóga", listOf("a@x.cz")))
    }

    @Test
    fun buildAllowedPaymentTypesIncludesOnlyEnabledOnes() {
        assertEquals(listOf(PaymentType.BANK_TRANSFER), buildAllowedPaymentTypes(allowBankTransfer = true, allowOnSite = false))
        assertEquals(emptyList(), buildAllowedPaymentTypes(allowBankTransfer = false, allowOnSite = false))
        assertEquals(
            listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
            buildAllowedPaymentTypes(allowBankTransfer = true, allowOnSite = true),
        )
    }

    @Test
    fun buildCreateEventDefinitionRequestMapsFormFieldsAndFiltersOwnerEmails() {
        val request = buildCreateEventDefinitionRequest(sampleForm())
        assertEquals("Jóga", request.title)
        assertEquals(listOf("a@x.cz"), request.ownerEmails)
        assertEquals(150.0, request.defaultPrice)
        assertEquals(12, request.defaultCapacity)
        assertEquals(1.hours + 30.minutes, request.defaultDuration)
        assertEquals(listOf(PaymentType.BANK_TRANSFER), request.allowedPaymentTypes)
        assertEquals(true, request.showAttendeeCount)
    }

    @Test
    fun buildUpdateEventDefinitionRequestCarriesPropagateFlag() {
        val request = buildUpdateEventDefinitionRequest(sampleForm(), propagateToChildren = true)
        assertEquals(true, request.propagateToChildren)
        assertEquals(listOf("a@x.cz"), request.ownerEmails)
        assertEquals(1.hours + 30.minutes, request.defaultDuration)
    }
}
