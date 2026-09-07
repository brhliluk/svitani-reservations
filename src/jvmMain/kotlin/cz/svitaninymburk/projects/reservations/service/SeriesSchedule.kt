package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import kotlin.uuid.Uuid

/**
 * The schedule fields stored on EventSeries (startDate, endDate, lessonCount,
 * lessonDayOfWeek, lessonStartTime, lessonEndTime) are a derived cache of the
 * series' actual lesson instances. Call [refresh] after any mutation of a
 * series' lessons. Cancelled lessons do not count towards lessonCount — a
 * cancelled lesson does not happen, so it is not a lesson of the course — but
 * they still feed the date/time fields when every lesson is cancelled, so the
 * course keeps a meaningful term. A series with no lessons at all keeps its
 * last stored values because the DB columns are non-null and serve as the
 * empty-series fallback.
 */
class SeriesScheduleRefresher(
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
) {
    suspend fun refresh(seriesId: Uuid) {
        val series = eventSeriesRepository.get(seriesId) ?: return
        val lessons = eventInstanceRepository.findBySeries(seriesId)
        if (lessons.isEmpty()) return
        val active = lessons.filter { !it.isCancelled }
        val schedule = active.ifEmpty { lessons }
        eventSeriesRepository.update(
            series.copy(
                startDate = schedule.minOf { it.startDateTime.date },
                endDate = schedule.maxOf { it.startDateTime.date },
                lessonCount = active.size,
                lessonDayOfWeek = schedule.map { it.startDateTime.date.dayOfWeek }.distinct().singleOrNull(),
                lessonStartTime = schedule.map { it.startDateTime.time }.distinct().singleOrNull(),
                lessonEndTime = schedule.map { it.endDateTime.time }.distinct().singleOrNull(),
            )
        )
    }

    suspend fun refreshAll() {
        eventSeriesRepository.getAll(null).forEach { refresh(it.id) }
    }
}
