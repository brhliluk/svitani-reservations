package cz.svitaninymburk.projects.reservations.ui.events

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.ui.events.usecase.CourseLessonsLoader
import cz.svitaninymburk.projects.reservations.ui.events.usecase.LessonAction
import cz.svitaninymburk.projects.reservations.ui.events.usecase.lessonAction
import cz.svitaninymburk.projects.reservations.ui.events.usecase.isPastLesson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

private fun lesson(day: Int) = EventInstance(
    id = Uuid.random(),
    definitionId = Uuid.random(),
    seriesId = Uuid.random(),
    title = "Jóga",
    description = "",
    startDateTime = LocalDateTime(2099, 6, day, 10, 0),
    endDateTime = LocalDateTime(2099, 6, day, 11, 0),
    price = 100.0,
    capacity = 10,
)

private class CountingLoader(var result: Either<EventError, List<EventInstance>>) : CourseLessonsLoader {
    var calls = 0
    override suspend fun lessons(): Either<EventError, List<EventInstance>> {
        calls++
        return result
    }
}

// Dispatchers.Unconfined: fake loader nikde neuspí, takže launch doběhne hned.
private fun model(loader: CourseLessonsLoader) = CourseLessonsModel(CoroutineScope(Dispatchers.Unconfined), loader)

class CourseLessonsModelSpec {

    @Test
    fun lessonsLoadOnFirstExpandSortedByStart() {
        val loader = CountingLoader(Either.Right(listOf(lesson(8), lesson(1))))
        val model = model(loader)

        model.toggle()

        assertTrue(model.isExpanded)
        assertEquals(listOf(1, 8), model.lessons?.map { it.startDateTime.day })
        assertEquals(1, loader.calls)
    }

    @Test
    fun collapsingAndExpandingAgainDoesNotReload() {
        val loader = CountingLoader(Either.Right(listOf(lesson(1))))
        val model = model(loader)

        model.toggle()
        model.toggle()
        assertFalse(model.isExpanded)
        model.toggle()

        assertEquals(1, loader.calls)
    }

    @Test
    fun errorIsShownInlineAndRetryClearsIt() {
        val loader = CountingLoader(Either.Left(EventError.EventSeriesNotFound("x")))
        val model = model(loader)

        model.toggle()
        assertNotNull(model.errorMessage)
        assertNull(model.lessons)

        loader.result = Either.Right(listOf(lesson(1)))
        model.load()

        assertNull(model.errorMessage)
        assertEquals(1, model.lessons?.size)
    }

    @Test
    fun lessonIsPastOnlyAfterItEnds() {
        val lesson = lesson(1)
        assertFalse(isPastLesson(lesson, LocalDateTime(2099, 6, 1, 10, 30)))
        assertTrue(isPastLesson(lesson, LocalDateTime(2099, 6, 1, 11, 0)))
    }

    private val now = Instant.parse("2099-05-01T10:00:00Z")
    private fun dropIn() = lesson(1).copy(isDropIn = true)

    @Test
    fun onlyDropInLessonsCanBeReservedAlone() {
        assertEquals(LessonAction.NONE, lessonAction(lesson(1), isPast = false, now = now))
        assertEquals(LessonAction.RESERVE, lessonAction(dropIn(), isPast = false, now = now))
    }

    @Test
    fun pastOrCancelledLessonHasNoAction() {
        assertEquals(LessonAction.NONE, lessonAction(dropIn(), isPast = true, now = now))
        assertEquals(LessonAction.NONE, lessonAction(dropIn().copy(isCancelled = true), isPast = false, now = now))
    }

    @Test
    fun fullLessonOffersWaitlistUntilItFillsUp() {
        val full = dropIn().copy(occupiedSpots = 10, waitlistCapacity = 2)
        assertEquals(LessonAction.WAITLIST, lessonAction(full, isPast = false, now = now))
        assertEquals(LessonAction.FULL, lessonAction(full.copy(occupiedWaitlist = 2), isPast = false, now = now))
        assertEquals(LessonAction.FULL, lessonAction(full.copy(waitlistCapacity = 0), isPast = false, now = now))
    }

    @Test
    fun closedReservationWinsOverFreeSpots() {
        val closed = dropIn().copy(reservationClosesAt = Instant.parse("2099-04-30T10:00:00Z"))
        assertEquals(LessonAction.CLOSED, lessonAction(closed, isPast = false, now = now))
    }
}
