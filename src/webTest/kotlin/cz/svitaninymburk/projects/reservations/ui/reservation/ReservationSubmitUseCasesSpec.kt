package cz.svitaninymburk.projects.reservations.ui.reservation

import arrow.core.left
import arrow.core.right
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitAction
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitter
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.reservationSubmitAction
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val instanceTarget = ReservationTarget.Instance(
    EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Beseda",
        description = "",
        startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 6, 1, 11, 0),
        price = 100.0,
        capacity = 10,
    ),
)

private val seriesTarget = ReservationTarget.Series(
    EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Kurz",
        description = "",
        price = 400.0,
        capacity = 10,
        occupiedSpots = 0,
        startDate = LocalDate(2099, 6, 1),
        endDate = LocalDate(2099, 7, 1),
        lessonCount = 4,
    ),
)

private fun formData(asWaitlist: Boolean = false) = ReservationFormData(
    name = "Jan",
    surname = "Novák",
    email = "jan@test.cz",
    phone = "+420123456789",
    seats = 1,
    paymentType = PaymentInfo.Type.BANK_TRANSFER,
    customValues = emptyMap(),
    asWaitlist = asWaitlist,
)

private fun reservation() = Reservation(
    id = Uuid.random(),
    reference = Reference.Instance(instanceTarget.id),
    contactName = "Jan Novák",
    contactEmail = "jan@test.cz",
    seatCount = 1,
    totalPrice = 100.0,
    status = Reservation.Status.PENDING_PAYMENT,
    createdAt = Clock.System.now(),
    customValues = emptyMap(),
    paymentType = PaymentInfo.Type.BANK_TRANSFER,
)

// Dispatchers.Unconfined: fake submitter nikde neuspí, takže launch doběhne
// synchronně ještě před assertem — viz ScreenModelSpec.
private class TestSubmitModel(
    submitter: ReservationSubmitter,
    private val reserved: MutableList<Reservation>,
    private val wrapError: Boolean = false,
) : ReservationSubmittingModel(CoroutineScope(Dispatchers.Unconfined), submitter, { reserved.add(it) }) {
    override fun reservationErrorMessage(localized: String): String =
        if (wrapError) "nezdařilo se: $localized" else localized
}

class ReservationSubmitActionSpec {

    @Test
    fun instanceWithoutWaitlistReservesInstance() {
        assertEquals(ReservationSubmitAction.ReserveInstance, reservationSubmitAction(instanceTarget, asWaitlist = false))
    }

    @Test
    fun instanceWithWaitlistJoinsInstanceWaitlist() {
        assertEquals(ReservationSubmitAction.JoinWaitlistInstance, reservationSubmitAction(instanceTarget, asWaitlist = true))
    }

    @Test
    fun seriesWithoutWaitlistReservesSeries() {
        assertEquals(ReservationSubmitAction.ReserveSeries, reservationSubmitAction(seriesTarget, asWaitlist = false))
    }

    @Test
    fun seriesWithWaitlistJoinsSeriesWaitlist() {
        assertEquals(ReservationSubmitAction.JoinWaitlistSeries, reservationSubmitAction(seriesTarget, asWaitlist = true))
    }
}

class ReservationSubmittingModelSpec {

    @Test
    fun successfulSubmitHandsOverReservationAndShowsNoToast() {
        val created = reservation()
        val reserved = mutableListOf<Reservation>()
        val model = TestSubmitModel({ _, _, _ -> created.right() }, reserved)

        model.submitReservation(instanceTarget, formData(), userId = null)

        assertEquals(listOf(created), reserved)
        assertNull(model.toast)
        assertFalse(model.isSubmitting)
    }

    @Test
    fun failedSubmitShowsErrorToastAndKeepsUserOnTheForm() {
        val reserved = mutableListOf<Reservation>()
        val model = TestSubmitModel({ _, _, _ -> ReservationError.CapacityExceeded.left() }, reserved)

        model.submitReservation(instanceTarget, formData(), userId = null)

        assertTrue(reserved.isEmpty())
        assertEquals(ReservationError.CapacityExceeded.localizedMessage(strings.value), model.toast?.message)
        assertEquals(ToastType.Error, model.toast?.type)
        assertFalse(model.isSubmitting)
    }

    @Test
    fun screenCanWrapTheErrorMessage() {
        val model = TestSubmitModel({ _, _, _ -> ReservationError.CapacityExceeded.left() }, mutableListOf(), wrapError = true)

        model.submitReservation(instanceTarget, formData(), userId = null)

        assertEquals(
            "nezdařilo se: ${ReservationError.CapacityExceeded.localizedMessage(strings.value)}",
            model.toast?.message,
        )
    }

    /**
     * Regrese k duplicitám z 18. 8. 2026: Kilua RPC při chybě vyhazuje výjimku,
     * takže se `isSubmitting` musí nulovat ve finally — jinak spinner visí navždy
     * a uživatel odešle rezervaci znovu.
     */
    @Test
    fun thrownRpcErrorStopsTheSpinner() {
        val model = TestSubmitModel({ _, _, _ -> throw IllegalStateException("connection lost") }, mutableListOf())

        model.submitReservation(instanceTarget, formData(), userId = null)

        assertFalse(model.isSubmitting)
    }

    /** Rezervace mohla na serveru vzniknout — netvrdit, že selhala. */
    @Test
    fun thrownRpcErrorReportsUnknownOutcomeRatherThanFailure() {
        val model = TestSubmitModel({ _, _, _ -> throw IllegalStateException("connection lost") }, mutableListOf(), wrapError = true)

        model.submitReservation(instanceTarget, formData(), userId = null)

        assertEquals(strings.value.reservationOutcomeUnknown, model.toast?.message)
        assertEquals(ToastType.Error, model.toast?.type)
    }
}
