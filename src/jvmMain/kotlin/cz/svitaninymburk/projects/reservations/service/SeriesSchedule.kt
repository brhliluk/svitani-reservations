package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import kotlin.uuid.Uuid

/**
 * The schedule fields stored on EventSeries (startDate, endDate, lessonCount,
 * lessonDayOfWeek, lessonStartTime, lessonEndTime) are a derived cache of the
 * series' actual lesson instances. Call [refresh] after any mutation of a
 * series' lessons. Cancelled lessons count (they still occupy a slot in the
 * course); a series with no lessons keeps its last stored values because the
 * DB columns are non-null and serve as the empty-series fallback.
 */
class SeriesScheduleRefresher(
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
) {
    suspend fun refresh(seriesId: Uuid) {
        val series = eventSeriesRepository.get(seriesId) ?: return
        val lessons = eventInstanceRepository.findBySeries(seriesId)
        if (lessons.isEmpty()) return
        eventSeriesRepository.update(
            series.copy(
                startDate = lessons.minOf { it.startDateTime.date },
                endDate = lessons.maxOf { it.startDateTime.date },
                lessonCount = lessons.size,
                lessonDayOfWeek = lessons.map { it.startDateTime.date.dayOfWeek }.distinct().singleOrNull(),
                lessonStartTime = lessons.map { it.startDateTime.time }.distinct().singleOrNull(),
                lessonEndTime = lessons.map { it.endDateTime.time }.distinct().singleOrNull(),
            )
        )
    }

    suspend fun refreshAll() {
        eventSeriesRepository.getAll(null).forEach { refresh(it.id) }
    }
}
