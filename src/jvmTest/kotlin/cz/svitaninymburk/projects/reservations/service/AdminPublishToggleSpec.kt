package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AdminPublishToggleSpec {

    private fun service(
        instanceRepo: InMemoryEventInstanceRepository,
        seriesRepo: InMemoryEventSeriesRepository,
    ) = AdminDashboardService(
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = InMemoryReservationRepository(),
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = WalletService(InMemoryWalletRepository()),
    )

    @Test
    fun `setInstancePublished flips the flag`() = runBlocking {
        val repo = InMemoryEventInstanceRepository()
        val instance = EventInstance(
            id = Uuid.random(), definitionId = Uuid.random(), title = "I", description = "D",
            startDateTime = LocalDateTime(2099, 1, 1, 9, 0), endDateTime = LocalDateTime(2099, 1, 1, 10, 0),
            price = 100.0, capacity = 10, isPublished = false,
        )
        repo.create(instance)
        val result = service(repo, InMemoryEventSeriesRepository()).setInstancePublished(instance.id, true)
        assertTrue(result.isRight())
        assertEquals(true, repo.get(instance.id)?.isPublished)
    }

    @Test
    fun `setSeriesPublished flips the flag`() = runBlocking {
        val repo = InMemoryEventSeriesRepository()
        val series = EventSeries(
            id = Uuid.random(), definitionId = Uuid.random(), title = "S", description = "D",
            price = 100.0, capacity = 10,
            startDate = LocalDate(2099, 1, 1), endDate = LocalDate(2099, 3, 1), lessonCount = 5,
            isPublished = false,
        )
        repo.create(series)
        val result = service(InMemoryEventInstanceRepository(), repo).setSeriesPublished(series.id, true)
        assertTrue(result.isRight())
        assertEquals(true, repo.get(series.id)?.isPublished)
    }
}
