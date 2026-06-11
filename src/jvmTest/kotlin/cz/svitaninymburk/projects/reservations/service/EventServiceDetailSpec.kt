package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
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

class EventServiceDetailSpec {

    private fun makeService(
        instanceRepo: InMemoryEventInstanceRepository = InMemoryEventInstanceRepository(),
        seriesRepo: InMemoryEventSeriesRepository = InMemoryEventSeriesRepository(),
    ) = EventService(InMemoryEventDefinitionRepository(), instanceRepo, seriesRepo)

    private fun makeInstance(
        id: Uuid = Uuid.random(),
        seriesId: Uuid? = null,
        start: LocalDateTime = LocalDateTime(2027, 7, 15, 9, 0),
        end: LocalDateTime = LocalDateTime(2027, 7, 15, 10, 0),
    ) = EventInstance(
        id = id,
        definitionId = Uuid.random(),
        seriesId = seriesId,
        title = "Jóga",
        description = "Popis",
        startDateTime = start,
        endDateTime = end,
        price = 200.0,
        capacity = 10,
        isPublished = true,
    )

    private fun makeSeries(id: Uuid = Uuid.random()) = EventSeries(
        id = id,
        definitionId = Uuid.random(),
        title = "Kurz jógy",
        description = "Popis kurzu",
        price = 1800.0,
        capacity = 12,
        startDate = LocalDate(2027, 9, 1),
        endDate = LocalDate(2027, 12, 15),
        lessonCount = 10,
        isPublished = true,
    )

    @Test
    fun `getInstance returns instance when found`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val instance = makeInstance()
        instanceRepo.create(instance)
        val service = makeService(instanceRepo = instanceRepo)

        val result = service.getInstance(instance.id)

        assertEquals(instance, result.getOrNull())
    }

    @Test
    fun `getInstance returns EventInstanceNotFound when missing`() = runBlocking {
        val service = makeService()
        val missingId = Uuid.random()

        val result = service.getInstance(missingId)

        assertEquals(EventError.EventInstanceNotFound(missingId.toString()), result.leftOrNull())
    }

    @Test
    fun `getSeriesDetail returns series with lessons sorted by start`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val series = makeSeries()
        seriesRepo.create(series)
        val later = makeInstance(seriesId = series.id, start = LocalDateTime(2027, 9, 15, 9, 0), end = LocalDateTime(2027, 9, 15, 10, 0))
        val earlier = makeInstance(seriesId = series.id, start = LocalDateTime(2027, 9, 8, 9, 0), end = LocalDateTime(2027, 9, 8, 10, 0))
        val unrelated = makeInstance()
        instanceRepo.create(later)
        instanceRepo.create(earlier)
        instanceRepo.create(unrelated)
        val service = makeService(instanceRepo = instanceRepo, seriesRepo = seriesRepo)

        val result = service.getSeriesDetail(series.id)

        val detail: SeriesDetailResponse? = result.getOrNull()
        assertEquals(series, detail?.series)
        assertEquals(listOf(earlier, later), detail?.lessons)
        assertTrue(detail?.lessons?.none { it.id == unrelated.id } == true)
    }

    @Test
    fun `getSeriesDetail returns empty lessons when series has none`() = runBlocking {
        val seriesRepo = InMemoryEventSeriesRepository()
        val series = makeSeries()
        seriesRepo.create(series)
        val service = makeService(seriesRepo = seriesRepo)

        val result = service.getSeriesDetail(series.id)

        assertEquals(series, result.getOrNull()?.series)
        assertEquals(emptyList(), result.getOrNull()?.lessons)
    }

    @Test
    fun `getSeriesDetail returns EventSeriesNotFound when missing`() = runBlocking {
        val service = makeService()
        val missingId = Uuid.random()

        val result = service.getSeriesDetail(missingId)

        assertEquals(EventError.EventSeriesNotFound(missingId.toString()), result.leftOrNull())
    }
}
