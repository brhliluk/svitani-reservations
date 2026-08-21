package cz.svitaninymburk.projects.reservations.ui.admin.events

import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.EventDefinitionFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.buildCreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.buildUpdateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.EventInstanceCreateFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.EventInstanceEditFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.buildCreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.buildUpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesCreateFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesEditFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildCreateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildUpdateEventSeriesRequest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Přepínač "povolit rezervaci více míst" musí projít z admin formuláře až do requestu —
 * u šablony, kurzu i jednorázové akce, při vytváření i editaci.
 */
class AdminFormAllowMultipleSeatsSpec {

    private fun definitionForm(allowMultipleSeats: Boolean) = EventDefinitionFormData(
        title = "Šablona",
        description = "desc",
        ownerEmails = listOf("a@x.cz"),
        price = 100.0,
        capacity = 10,
        durationHours = 1,
        durationMinutes = 0,
        allowBankTransfer = true,
        allowOnSite = false,
        customFields = emptyList(),
        showAttendeeCount = true,
        allowMultipleSeats = allowMultipleSeats,
    )

    private fun seriesCreateForm(allowMultipleSeats: Boolean) = EventSeriesCreateFormData(
        title = "Kurz",
        description = "desc",
        ownerEmails = listOf("a@x.cz"),
        price = 1500.0,
        lessonPrice = null,
        capacity = 12,
        waitlistCapacity = 3,
        allowBankTransfer = true,
        allowOnSite = false,
        showAttendeeCount = true,
        allowMultipleSeats = allowMultipleSeats,
        customFields = emptyList(),
        deadlineMessage = "",
    )

    private fun seriesEditForm(allowMultipleSeats: Boolean) = EventSeriesEditFormData(
        title = "Kurz",
        description = "desc",
        ownerEmails = listOf("a@x.cz"),
        price = 1500.0,
        lessonPrice = null,
        capacity = 12,
        waitlistCapacity = 3,
        allowBankTransfer = true,
        allowOnSite = false,
        showAttendeeCount = true,
        allowMultipleSeats = allowMultipleSeats,
        lessonRefundAmount = null,
        customFields = emptyList(),
        deadlineMessage = "",
    )

    private fun instanceCreateForm(allowMultipleSeats: Boolean) = EventInstanceCreateFormData(
        title = "Akce",
        description = "desc",
        ownerEmails = listOf("a@x.cz"),
        price = 100.0,
        capacity = 10,
        waitlistCapacity = 3,
        durationHours = 1,
        durationMinutes = 0,
        allowBankTransfer = true,
        allowOnSite = false,
        showAttendeeCount = true,
        allowMultipleSeats = allowMultipleSeats,
        customFields = emptyList(),
        deadlineMessage = "",
    )

    private fun instanceEditForm(allowMultipleSeats: Boolean) = EventInstanceEditFormData(
        title = "Akce",
        description = "desc",
        ownerEmails = listOf("a@x.cz"),
        price = 100.0,
        capacity = 10,
        waitlistCapacity = 3,
        allowBankTransfer = true,
        allowOnSite = false,
        isDropIn = false,
        showAttendeeCount = true,
        allowMultipleSeats = allowMultipleSeats,
        customFields = emptyList(),
        deadlineMessage = "",
    )

    @Test
    fun definitionCreateRequestCarriesAllowMultipleSeats() {
        assertEquals(false, buildCreateEventDefinitionRequest(definitionForm(false)).allowMultipleSeats)
        assertEquals(true, buildCreateEventDefinitionRequest(definitionForm(true)).allowMultipleSeats)
    }

    @Test
    fun definitionUpdateRequestCarriesAllowMultipleSeats() {
        assertEquals(
            false,
            buildUpdateEventDefinitionRequest(definitionForm(false), propagateToChildren = true).allowMultipleSeats,
        )
        assertEquals(
            true,
            buildUpdateEventDefinitionRequest(definitionForm(true), propagateToChildren = false).allowMultipleSeats,
        )
    }

    private fun buildSeriesCreate(allowMultipleSeats: Boolean) = buildCreateEventSeriesRequest(
        form = seriesCreateForm(allowMultipleSeats),
        definitionId = Uuid.random(),
        startDate = LocalDate(2099, 2, 2),
        endDate = LocalDate(2099, 3, 2),
        lessonCount = 4,
        lessonDayOfWeek = null,
        lessonStartTime = null,
        lessonEndTime = null,
        customLessons = null,
        reservationDeadline = null,
        isPublished = false,
    )

    @Test
    fun seriesCreateRequestCarriesAllowMultipleSeats() {
        assertEquals(false, buildSeriesCreate(false).allowMultipleSeats)
        assertEquals(true, buildSeriesCreate(true).allowMultipleSeats)
    }

    @Test
    fun seriesUpdateRequestCarriesAllowMultipleSeats() {
        assertEquals(
            false,
            buildUpdateEventSeriesRequest(seriesEditForm(false), reservationDeadline = null).allowMultipleSeats,
        )
        assertEquals(
            true,
            buildUpdateEventSeriesRequest(seriesEditForm(true), reservationDeadline = null).allowMultipleSeats,
        )
    }

    private fun buildInstanceCreate(allowMultipleSeats: Boolean) = buildCreateEventInstanceRequest(
        form = instanceCreateForm(allowMultipleSeats),
        definitionId = Uuid.random(),
        startDateTime = LocalDateTime(2099, 2, 2, 10, 0),
        reservationDeadline = null,
        isPublished = false,
    )

    @Test
    fun instanceCreateRequestCarriesAllowMultipleSeats() {
        assertEquals(false, buildInstanceCreate(false).allowMultipleSeats)
        assertEquals(true, buildInstanceCreate(true).allowMultipleSeats)
    }

    @Test
    fun instanceUpdateRequestCarriesAllowMultipleSeats() {
        assertEquals(
            false,
            buildUpdateEventInstanceRequest(
                form = instanceEditForm(false),
                startDateTime = LocalDateTime(2099, 2, 2, 10, 0),
                endDateTime = LocalDateTime(2099, 2, 2, 11, 0),
                reservationDeadline = null,
            ).allowMultipleSeats,
        )
        assertEquals(
            true,
            buildUpdateEventInstanceRequest(
                form = instanceEditForm(true),
                startDateTime = LocalDateTime(2099, 2, 2, 10, 0),
                endDateTime = LocalDateTime(2099, 2, 2, 11, 0),
                reservationDeadline = null,
            ).allowMultipleSeats,
        )
    }
}
