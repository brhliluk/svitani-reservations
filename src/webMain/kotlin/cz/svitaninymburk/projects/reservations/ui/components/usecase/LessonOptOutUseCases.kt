package cz.svitaninymburk.projects.reservations.ui.components.usecase

import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonItem
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonsView
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import kotlin.time.Instant
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---
//
// Uzávěrku ani začátek lekce si tu nepočítáme — prohlížeč nemá databázi časových
// pásem, takže Europe/Prague neumí a v zóně návštěvníka by vycházelo něco jiného
// než na serveru. Obojí proto chodí ze serveru jako absolutní čas.

/** Co se stane s penězi, když se člověk z lekce odhlásí právě teď. */
enum class LessonRefundPreview {
    /** Vrátí se kredit do peněženky. */
    REFUND_ELIGIBLE,

    /** Po uzávěrce — omluvenka projde, ale bez kreditu. */
    WINDOW_PASSED,

    /** Nezaplaceno, není co vracet. */
    NOT_PAID,

    /** Kurz kredit za jednotlivé lekce nevrací, nebo je už vyčerpaný. */
    NO_REFUND_CONFIGURED,
}

/**
 * Kolik kreditu omluvenka reálně přinese. Zrcadlí strop ze serveru — souhrn
 * omluvenek nesmí přerůst zaplacenou částku.
 */
fun lessonRefundAmount(view: SeriesLessonsView): Double {
    val perLesson = (view.lessonRefundAmount ?: 0.0) * view.seatCount
    if (perLesson <= 0.0) return 0.0
    return minOf(perLesson, view.paidAmount - view.alreadyRefunded).coerceAtLeast(0.0)
}

fun lessonRefundPreview(view: SeriesLessonsView, lesson: SeriesLessonItem, now: Instant): LessonRefundPreview {
    val deadline = lesson.optOutDeadline
    return when {
        view.paidAmount <= 0.0 -> LessonRefundPreview.NOT_PAID
        deadline != null && now > deadline -> LessonRefundPreview.WINDOW_PASSED
        lessonRefundAmount(view) <= 0.0 -> LessonRefundPreview.NO_REFUND_CONFIGURED
        else -> LessonRefundPreview.REFUND_ELIGIBLE
    }
}

/** Z lekce se lze odhlásit, dokud nezačala a není zrušená ani už odhlášená. */
fun canOptOut(lesson: SeriesLessonItem, now: Instant): Boolean {
    if (lesson.isCancelled || lesson.isOptedOut) return false
    val startsAt = lesson.startsAt ?: return true
    return now < startsAt
}

/**
 * Pole na kód peněženky má smysl jen tam, kde kredit reálně vznikne a člověk
 * nemá účet — jinak peněženku určuje přihlášení.
 */
fun needsWalletCodeInput(isAnonymousReservation: Boolean, preview: LessonRefundPreview): Boolean =
    isAnonymousReservation && preview == LessonRefundPreview.REFUND_ELIGIBLE

class LessonOptOutQueries(private val service: ReservationServiceInterface) {
    suspend fun lessons(reservationId: Uuid) = service.getSeriesLessons(reservationId)
}

class LessonOptOutMutations(private val service: ReservationServiceInterface) {
    suspend fun optOut(reservationId: Uuid, instanceId: Uuid, walletCode: String?, force: Boolean) =
        service.cancelReservation(
            reservationId = reservationId,
            instanceId = instanceId,
            walletCode = walletCode,
            force = force,
        )
}
