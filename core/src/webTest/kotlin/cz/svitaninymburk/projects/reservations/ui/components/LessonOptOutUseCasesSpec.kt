package cz.svitaninymburk.projects.reservations.ui.components

import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonItem
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonsView
import cz.svitaninymburk.projects.reservations.ui.components.usecase.LessonRefundPreview
import cz.svitaninymburk.projects.reservations.ui.components.usecase.canOptOut
import cz.svitaninymburk.projects.reservations.ui.components.usecase.lessonRefundAmount
import cz.svitaninymburk.projects.reservations.ui.components.usecase.lessonRefundPreview
import cz.svitaninymburk.projects.reservations.ui.components.usecase.lessonRefundRate
import cz.svitaninymburk.projects.reservations.ui.components.usecase.needsWalletCodeInput
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Náhled kreditu v odhlašovacím dialogu musí říct totéž, co pak udělá server —
 * včetně stropu na zaplacenou částku a přepočtu na počet míst. Uzávěrku ani
 * začátek lekce si klient nepočítá, chodí ze serveru jako absolutní čas.
 */
class LessonOptOutUseCasesSpec {

    private val start = Instant.parse("2099-06-10T08:00:00Z")
    private val deadline = Instant.parse("2099-06-09T16:00:00Z")
    private val beforeDeadline = Instant.parse("2099-06-08T10:00:00Z")
    private val afterDeadline = Instant.parse("2099-06-10T06:00:00Z")

    private fun view(
        paidAmount: Double = 1000.0,
        alreadyRefunded: Double = 0.0,
        lessonRefundAmount: Double? = 100.0,
        seatCount: Int = 1,
        isAnonymous: Boolean = true,
        lessonCredit: Double? = null,
    ) = SeriesLessonsView(
        lessons = emptyList(),
        paidAmount = paidAmount,
        alreadyRefunded = alreadyRefunded,
        lessonRefundAmount = lessonRefundAmount,
        lessonCredit = lessonCredit,
        seatCount = seatCount,
        isAnonymousReservation = isAnonymous,
    )

    private fun lesson(
        isCancelled: Boolean = false,
        isOptedOut: Boolean = false,
        startsAt: Instant? = start,
        optOutDeadline: Instant? = deadline,
    ) = SeriesLessonItem(
        instanceId = Uuid.random(),
        startDateTime = LocalDateTime(2099, 6, 10, 10, 0),
        endDateTime = LocalDateTime(2099, 6, 10, 11, 0),
        isCancelled = isCancelled,
        isOptedOut = isOptedOut,
        isLateCancellation = false,
        startsAt = startsAt,
        optOutDeadline = optOutDeadline,
    )

    @Test
    fun unpaidReservationGetsNoCredit() {
        assertEquals(
            LessonRefundPreview.NOT_PAID,
            lessonRefundPreview(view(paidAmount = 0.0), lesson(), beforeDeadline),
        )
    }

    @Test
    fun unpaidReservationAfterDeadlinePromisesNothing() {
        // „Připíšeme po zaplacení“ by bylo nepravdivé — pozdní omluvenka nedostane nic ani pak.
        assertEquals(
            LessonRefundPreview.WINDOW_PASSED,
            lessonRefundPreview(view(paidAmount = 0.0), lesson(), afterDeadline),
        )
    }

    @Test
    fun unpaidCourseWithoutLessonCreditPromisesNothing() {
        assertEquals(
            LessonRefundPreview.NO_REFUND_CONFIGURED,
            lessonRefundPreview(view(paidAmount = 0.0, lessonRefundAmount = null), lesson(), beforeDeadline),
        )
    }

    @Test
    fun promiseAfterPaymentShowsFullRateEvenWhenUnpaid() {
        assertEquals(200.0, lessonRefundRate(view(paidAmount = 0.0, seatCount = 2)))
    }

    @Test
    fun noCreditAfterDeadline() {
        assertEquals(
            LessonRefundPreview.WINDOW_PASSED,
            lessonRefundPreview(view(), lesson(), afterDeadline),
        )
    }

    @Test
    fun courseWithoutConfiguredCreditRefundsNothing() {
        assertEquals(
            LessonRefundPreview.NO_REFUND_CONFIGURED,
            lessonRefundPreview(view(lessonRefundAmount = null), lesson(), beforeDeadline),
        )
    }

    @Test
    fun exhaustedCapAllowsNoFurtherCredit() {
        // Zaplaceno 1000, vráceno 1000 — další omluvenka je možná, ale bez kreditu.
        val exhausted = view(paidAmount = 1000.0, alreadyRefunded = 1000.0)
        assertEquals(0.0, lessonRefundAmount(exhausted))
        assertEquals(
            LessonRefundPreview.NO_REFUND_CONFIGURED,
            lessonRefundPreview(exhausted, lesson(), beforeDeadline),
        )
    }

    @Test
    fun creditIsCappedByRemainderOfPaidAmount() {
        // 100 Kč za lekci, ale do stropu zbývá jen 40.
        assertEquals(40.0, lessonRefundAmount(view(paidAmount = 1000.0, alreadyRefunded = 960.0)))
    }

    @Test
    fun creditIsMultipliedBySeatCount() {
        assertEquals(300.0, lessonRefundAmount(view(seatCount = 3)))
    }

    @Test
    fun timelyOptOutFromPaidCourseRefundsCredit() {
        assertEquals(
            LessonRefundPreview.REFUND_ELIGIBLE,
            lessonRefundPreview(view(), lesson(), beforeDeadline),
        )
    }

    @Test
    fun onlyFutureLessonNeitherCancelledNorOptedOutCanBeOptedOut() {
        assertTrue(canOptOut(lesson(), beforeDeadline), "běžná budoucí lekce")
        assertFalse(canOptOut(lesson(isCancelled = true), beforeDeadline), "zrušená lekce")
        assertFalse(canOptOut(lesson(isOptedOut = true), beforeDeadline), "už odhlášená lekce")
        assertFalse(canOptOut(lesson(), start + 1.hours), "lekce, která už začala")
    }

    @Test
    fun optOutIsNotBlockedWithoutServerTimes() {
        // Starší server by pole neposlal; UI kvůli tomu nesmí tlačítko schovat.
        assertTrue(canOptOut(lesson(startsAt = null), afterDeadline))
    }

    @Test
    fun walletCodeFieldIsAskedOnlyWithoutAccountAndOnlyWhenCreditArises() {
        assertTrue(needsWalletCodeInput(true, LessonRefundPreview.REFUND_ELIGIBLE))
        assertFalse(needsWalletCodeInput(false, LessonRefundPreview.REFUND_ELIGIBLE), "přihlášený má peněženku od účtu")
        assertFalse(needsWalletCodeInput(true, LessonRefundPreview.WINDOW_PASSED), "po uzávěrce není co připsat")
        assertFalse(needsWalletCodeInput(true, LessonRefundPreview.NOT_PAID))
    }

    @Test
    fun serverComputedCreditWinsOverRate() {
        // Kurz bez ruční sazby: server pošle poměrnou část ceny rezervace.
        val v = view(lessonRefundAmount = null, lessonCredit = 214.0, seatCount = 3)
        assertEquals(214.0, lessonRefundAmount(v))
        assertEquals(LessonRefundPreview.REFUND_ELIGIBLE, lessonRefundPreview(v, lesson(), beforeDeadline))
    }

    @Test
    fun serverComputedCreditIsStillCappedByPaidAmount() {
        val v = view(paidAmount = 300.0, alreadyRefunded = 200.0, lessonRefundAmount = null, lessonCredit = 214.0)
        assertEquals(100.0, lessonRefundAmount(v))
    }
}
