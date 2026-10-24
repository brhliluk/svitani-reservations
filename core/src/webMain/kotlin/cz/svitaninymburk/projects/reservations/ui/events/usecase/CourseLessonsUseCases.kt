package cz.svitaninymburk.projects.reservations.ui.events.usecase

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Lekce, která už skončila — na kartě kurzu se jen ztlumí, ať je vidět celý rozpis. */
fun isPastLesson(lesson: EventInstance, now: LocalDateTime): Boolean = lesson.endDateTime <= now

/** Co jde s lekcí udělat přímo z karty kurzu. */
enum class LessonAction { NONE, RESERVE, WAITLIST, FULL, CLOSED }

/**
 * Jednotlivě se dá přihlásit jen na lekci, kterou admin pustil do prodeje po jedné
 * (drop-in) — ostatní jdou jen s celým kurzem. Pravidla tlačítka jsou stejná jako
 * na kartě jednorázové akce.
 */
fun lessonAction(lesson: EventInstance, isPast: Boolean, now: Instant): LessonAction = when {
    !lesson.isDropIn || lesson.isCancelled || isPast -> LessonAction.NONE
    lesson.isReservationClosed(now) -> LessonAction.CLOSED
    !lesson.isFull -> LessonAction.RESERVE
    lesson.hasWaitlist && !lesson.isWaitlistFull -> LessonAction.WAITLIST
    else -> LessonAction.FULL
}

/** Seam pro testy [cz.svitaninymburk.projects.reservations.ui.events.CourseLessonsModel]. */
fun interface CourseLessonsLoader {
    suspend fun lessons(): Either<EventError, List<EventInstance>>
}

class CourseLessonsQueries(
    private val event: EventServiceInterface,
    private val seriesId: Uuid,
) : CourseLessonsLoader {
    override suspend fun lessons() = event.getSeriesDetail(seriesId).map { it.lessons }
}
