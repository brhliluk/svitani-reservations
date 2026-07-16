package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.service.SeriesScheduleRefresher
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class SeriesScheduleRefresherSpec {

    private val definitionId = Uuid.random()

    // Stored values are deliberately wrong/stale so tests prove they get recomputed.
    private fun makeSeries(id: Uuid = Uuid.random()) = EventSeries(
        id = id,
        definitionId = definitionId,
        title = "Test Series",
        description = "desc",
        price = 500.0,
        capacity = 10,
        startDate = LocalDate(2026, 1, 1),
        endDate = LocalDate(2026, 1, 2),
        lessonCount = 99,
        lessonDayOfWeek = DayOfWeek.FRIDAY,
        lessonStartTime = LocalTime(8, 0),
        lessonEndTime = LocalTime(9, 0),
    )

    private fun makeLesson(
        seriesId: Uuid,
        start: LocalDateTime,
        end: LocalDateTime,
        isCancelled: Boolean = false,
    ) = EventInstance(
        id = Uuid.random(),
        definitionId = definitionId,
        seriesId = seriesId,
        title = "Lesson",
        description = "desc",
        startDateTime = start,
        endDateTime = end,
        price = 500.0,
        capacity = 10,
        isCancelled = isCancelled,
    )

    private data class Fixture(
        val refresher: SeriesScheduleRefresher,
        val seriesRepo: InMemoryEventSeriesRepository,
        val instanceRepo: InMemoryEventInstanceRepository,
    )

    private fun fixture(): Fixture {
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        return Fixture(SeriesScheduleRefresher(instanceRepo, seriesRepo), seriesRepo, instanceRepo)
    }

    @Test
    fun `refresh recomputes dates and lessonCount from lessons including cancelled`() = runBlocking {
        val (refresher, seriesRepo, instanceRepo) = fixture()
        val series = makeSeries()
        seriesRepo.create(series)
        instanceRepo.create(makeLesson(series.id, LocalDateTime(2026, 6, 1, 17, 0), LocalDateTime(2026, 6, 1, 18, 0)))
        instanceRepo.create(makeLesson(series.id, LocalDateTime(2026, 6, 8, 17, 0), LocalDateTime(2026, 6, 8, 18, 0)))
        instanceRepo.create(makeLesson(series.id, LocalDateTime(2026, 6, 15, 17, 0), LocalDateTime(2026, 6, 15, 18, 0), isCancelled = true))

        refresher.refresh(series.id)

        val updated = seriesRepo.get(series.id)
        assertNotNull(updated)
        assertEquals(LocalDate(2026, 6, 1), updated.startDate)
        assertEquals(LocalDate(2026, 6, 15), updated.endDate)
        assertEquals(3, updated.lessonCount)
    }

    @Test
    fun `refresh sets day and times when uniform across all lessons`() = runBlocking {
        val (refresher, seriesRepo, instanceRepo) = fixture()
        val series = makeSeries()
        seriesRepo.create(series)
        // 2026-06-01 and 2026-06-08 are both Mondays.
        instanceRepo.create(makeLesson(series.id, LocalDateTime(2026, 6, 1, 17, 0), LocalDateTime(2026, 6, 1, 18, 0)))
        instanceRepo.create(makeLesson(series.id, LocalDateTime(2026, 6, 8, 17, 0), LocalDateTime(2026, 6, 8, 18, 0)))

        refresher.refresh(series.id)

        val updated = seriesRepo.get(series.id)
        assertNotNull(updated)
        assertEquals(DayOfWeek.MONDAY, updated.lessonDayOfWeek)
        assertEquals(LocalTime(17, 0), updated.lessonStartTime)
        assertEquals(LocalTime(18, 0), updated.lessonEndTime)
    }

    @Test
    fun `refresh nulls day and times when schedule is irregular`() = runBlocking {
        val (refresher, seriesRepo, instanceRepo) = fixture()
        val series = makeSeries()
        seriesRepo.create(series)
        // Monday 17:00-18:00 and Tuesday 18:30-19:30 — nothing uniform.
        instanceRepo.create(makeLesson(series.id, LocalDateTime(2026, 6, 1, 17, 0), LocalDateTime(2026, 6, 1, 18, 0)))
        instanceRepo.create(makeLesson(series.id, LocalDateTime(2026, 6, 9, 18, 30), LocalDateTime(2026, 6, 9, 19, 30)))

        refresher.refresh(series.id)

        val updated = seriesRepo.get(series.id)
        assertNotNull(updated)
        assertNull(updated.lessonDayOfWeek)
        assertNull(updated.lessonStartTime)
        assertNull(updated.lessonEndTime)
    }

    @Test
    fun `refresh keeps stored values when series has no lessons`() = runBlocking {
        val (refresher, seriesRepo, _) = fixture()
        val series = makeSeries()
        seriesRepo.create(series)

        refresher.refresh(series.id)

        val updated = seriesRepo.get(series.id)
        assertNotNull(updated)
        assertEquals(LocalDate(2026, 1, 1), updated.startDate)
        assertEquals(LocalDate(2026, 1, 2), updated.endDate)
        assertEquals(99, updated.lessonCount)
    }

    @Test
    fun `refreshAll refreshes every series`() = runBlocking {
        val (refresher, seriesRepo, instanceRepo) = fixture()
        val a = makeSeries()
        val b = makeSeries()
        seriesRepo.create(a)
        seriesRepo.create(b)
        instanceRepo.create(makeLesson(a.id, LocalDateTime(2026, 6, 1, 17, 0), LocalDateTime(2026, 6, 1, 18, 0)))
        instanceRepo.create(makeLesson(b.id, LocalDateTime(2026, 7, 2, 9, 0), LocalDateTime(2026, 7, 2, 10, 0)))

        refresher.refreshAll()

        assertEquals(LocalDate(2026, 6, 1), seriesRepo.get(a.id)?.startDate)
        assertEquals(1, seriesRepo.get(a.id)?.lessonCount)
        assertEquals(LocalDate(2026, 7, 2), seriesRepo.get(b.id)?.startDate)
        assertEquals(1, seriesRepo.get(b.id)?.lessonCount)
    }
}
