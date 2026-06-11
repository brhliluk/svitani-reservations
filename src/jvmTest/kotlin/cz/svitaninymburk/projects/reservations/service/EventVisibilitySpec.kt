package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class EventVisibilitySpec {

    private fun instance(id: Uuid = Uuid.random(), published: Boolean) = EventInstance(
        id = id, definitionId = Uuid.random(), title = "I", description = "D",
        startDateTime = LocalDateTime(2099, 1, 1, 9, 0), endDateTime = LocalDateTime(2099, 1, 1, 10, 0),
        price = 100.0, capacity = 10, isPublished = published,
    )

    private fun series(id: Uuid = Uuid.random(), published: Boolean) = EventSeries(
        id = id, definitionId = Uuid.random(), title = "S", description = "D",
        price = 100.0, capacity = 10,
        startDate = LocalDate(2099, 1, 1), endDate = LocalDate(2099, 3, 1), lessonCount = 5,
        isPublished = published,
    )

    private fun service(
        instanceRepo: InMemoryEventInstanceRepository = InMemoryEventInstanceRepository(),
        seriesRepo: InMemoryEventSeriesRepository = InMemoryEventSeriesRepository(),
    ) = EventService(InMemoryEventDefinitionRepository(), instanceRepo, seriesRepo)

    @Test
    fun `getAllInstances returns only published`() = runBlocking {
        val repo = InMemoryEventInstanceRepository()
        repo.create(instance(published = true))
        repo.create(instance(published = false))
        val result = service(instanceRepo = repo).getAllInstances().getOrNull()!!
        assertEquals(1, result.size)
        assertTrue(result.all { it.isPublished })
    }

    @Test
    fun `getAllSeries returns only published`() = runBlocking {
        val repo = InMemoryEventSeriesRepository()
        repo.create(series(published = true))
        repo.create(series(published = false))
        val result = service(seriesRepo = repo).getAllSeries().getOrNull()!!
        assertEquals(1, result.size)
    }

    @Test
    fun `getInstance returns NotFound for hidden instance`() = runBlocking {
        val repo = InMemoryEventInstanceRepository()
        val hidden = instance(published = false)
        repo.create(hidden)
        val result = service(instanceRepo = repo).getInstance(hidden.id)
        assertEquals(EventError.EventInstanceNotFound(hidden.id.toString()), result.leftOrNull())
    }

    @Test
    fun `getSeriesDetail returns NotFound for hidden series`() = runBlocking {
        val repo = InMemoryEventSeriesRepository()
        val hidden = series(published = false)
        repo.create(hidden)
        val result = service(seriesRepo = repo).getSeriesDetail(hidden.id)
        assertEquals(EventError.EventSeriesNotFound(hidden.id.toString()), result.leftOrNull())
    }

    @Test
    fun `getDashboardData excludes hidden instances and series`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        instanceRepo.create(instance(published = true))
        instanceRepo.create(instance(published = false))
        seriesRepo.create(series(published = true))
        seriesRepo.create(series(published = false))
        val data = service(instanceRepo, seriesRepo).getDashboardData().getOrNull()!!
        assertEquals(1, data.instances.size)
        assertEquals(1, data.series.size)
    }
}
