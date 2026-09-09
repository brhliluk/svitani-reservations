package cz.svitaninymburk.projects.reservations.ui.components

import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonItem
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonsView
import cz.svitaninymburk.projects.reservations.ui.components.usecase.LessonRefundPreview
import cz.svitaninymburk.projects.reservations.ui.components.usecase.canOptOut
import cz.svitaninymburk.projects.reservations.ui.components.usecase.lessonRefundAmount
import cz.svitaninymburk.projects.reservations.ui.components.usecase.lessonRefundPreview
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
    ) = SeriesLessonsView(
        lessons = emptyList(),
        paidAmount = paidAmount,
        alreadyRefunded = alreadyRefunded,
        lessonRefundAmount = lessonRefundAmount,
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
    fun `nezaplacena rezervace kredit nedostane`() {
        assertEquals(
            LessonRefundPreview.NOT_PAID,
            lessonRefundPreview(view(paidAmount = 0.0), lesson(), beforeDeadline),
        )
    }

    @Test
    fun `po uzaverce kredit nevznikne`() {
        assertEquals(
            LessonRefundPreview.WINDOW_PASSED,
            lessonRefundPreview(view(), lesson(), afterDeadline),
        )
    }

    @Test
    fun `kurz bez nastaveneho kreditu nic nevraci`() {
        assertEquals(
            LessonRefundPreview.NO_REFUND_CONFIGURED,
            lessonRefundPreview(view(lessonRefundAmount = null), lesson(), beforeDeadline),
        )
    }

    @Test
    fun `vycerpany strop uz dalsi kredit nedovoli`() {
        // Zaplaceno 1000, vráceno 1000 — další omluvenka je možná, ale bez kreditu.
        val vycerpano = view(paidAmount = 1000.0, alreadyRefunded = 1000.0)
        assertEquals(0.0, lessonRefundAmount(vycerpano))
        assertEquals(
            LessonRefundPreview.NO_REFUND_CONFIGURED,
            lessonRefundPreview(vycerpano, lesson(), beforeDeadline),
        )
    }

    @Test
    fun `kredit se strope zbytkem do zaplacene castky`() {
        // 100 Kč za lekci, ale do stropu zbývá jen 40.
        assertEquals(40.0, lessonRefundAmount(view(paidAmount = 1000.0, alreadyRefunded = 960.0)))
    }

    @Test
    fun `kredit se nasobi poctem mist`() {
        assertEquals(300.0, lessonRefundAmount(view(seatCount = 3)))
    }

    @Test
    fun `vcasne odhlaseni ze zaplaceneho kurzu kredit vrati`() {
        assertEquals(
            LessonRefundPreview.REFUND_ELIGIBLE,
            lessonRefundPreview(view(), lesson(), beforeDeadline),
        )
    }

    @Test
    fun `odhlasit lze jen budouci lekci, ktera neni zrusena ani odhlasena`() {
        assertTrue(canOptOut(lesson(), beforeDeadline), "běžná budoucí lekce")
        assertFalse(canOptOut(lesson(isCancelled = true), beforeDeadline), "zrušená lekce")
        assertFalse(canOptOut(lesson(isOptedOut = true), beforeDeadline), "už odhlášená lekce")
        assertFalse(canOptOut(lesson(), start + 1.hours), "lekce, která už začala")
    }

    @Test
    fun `bez casu ze serveru se odhlaseni neblokuje`() {
        // Starší server by pole neposlal; UI kvůli tomu nesmí tlačítko schovat.
        assertTrue(canOptOut(lesson(startsAt = null), afterDeadline))
    }

    @Test
    fun `pole na kod penezenky se ptame jen bez uctu a jen kdyz kredit vznikne`() {
        assertTrue(needsWalletCodeInput(true, LessonRefundPreview.REFUND_ELIGIBLE))
        assertFalse(needsWalletCodeInput(false, LessonRefundPreview.REFUND_ELIGIBLE), "přihlášený má peněženku od účtu")
        assertFalse(needsWalletCodeInput(true, LessonRefundPreview.WINDOW_PASSED), "po uzávěrce není co připsat")
        assertFalse(needsWalletCodeInput(true, LessonRefundPreview.NOT_PAID))
    }
}
