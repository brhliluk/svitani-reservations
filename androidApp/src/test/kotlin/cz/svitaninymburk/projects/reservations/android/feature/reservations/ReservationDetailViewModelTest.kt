package cz.svitaninymburk.projects.reservations.android.feature.reservations

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.feature.reservations.detail.ReservationDetailViewModel
import cz.svitaninymburk.projects.reservations.android.repository.reservation.ReservationsRepository
import cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDateTime
import kotlin.test.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state contains the provided item`() {
        val item = mockItem(status = Reservation.Status.CONFIRMED)
        val vm = ReservationDetailViewModel(item, FakeReservationsRepositoryDetail())
        assertEquals(item, vm.uiState.value.item)
    }

    @Test
    fun `fetches payment info when PENDING_PAYMENT and BANK_TRANSFER`() = runTest {
        val paymentInfo = mockPaymentInfo()
        val item = mockItem(
            status = Reservation.Status.PENDING_PAYMENT,
            paymentType = PaymentInfo.Type.BANK_TRANSFER,
        )
        val vm = ReservationDetailViewModel(
            item,
            FakeReservationsRepositoryDetail(paymentInfoResult = Either.Right(paymentInfo)),
        )
        advanceUntilIdle()
        assertEquals(paymentInfo, vm.uiState.value.paymentInfo)
        assertFalse(vm.uiState.value.isLoadingPayment)
    }

    @Test
    fun `does NOT fetch payment info when status is CONFIRMED`() = runTest {
        val item = mockItem(
            status = Reservation.Status.CONFIRMED,
            paymentType = PaymentInfo.Type.BANK_TRANSFER,
        )
        val vm = ReservationDetailViewModel(item, FakeReservationsRepositoryDetail())
        advanceUntilIdle()
        assertNull(vm.uiState.value.paymentInfo)
        assertFalse(vm.uiState.value.isLoadingPayment)
    }

    @Test
    fun `does NOT fetch payment info when paymentType is ON_SITE`() = runTest {
        val item = mockItem(
            status = Reservation.Status.PENDING_PAYMENT,
            paymentType = PaymentInfo.Type.ON_SITE,
        )
        val vm = ReservationDetailViewModel(item, FakeReservationsRepositoryDetail())
        advanceUntilIdle()
        assertNull(vm.uiState.value.paymentInfo)
    }

    @Test
    fun `cancel success sets cancelSuccess flag and updates item status`() = runTest {
        val item = mockItem(status = Reservation.Status.CONFIRMED)
        val vm = ReservationDetailViewModel(
            item,
            FakeReservationsRepositoryDetail(cancelResult = Either.Right(Unit)),
        )
        vm.cancelReservation()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.cancelSuccess)
        assertEquals(Reservation.Status.CANCELLED, vm.uiState.value.item.status)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `cancel failure sets error`() = runTest {
        val error = RepositoryError.Server("CancelError", "Rezervaci nelze zrušit")
        val item = mockItem(status = Reservation.Status.CONFIRMED)
        val vm = ReservationDetailViewModel(
            item,
            FakeReservationsRepositoryDetail(cancelResult = Either.Left(error)),
        )
        vm.cancelReservation()
        advanceUntilIdle()
        assertEquals(error, vm.uiState.value.error)
        assertFalse(vm.uiState.value.cancelSuccess)
    }

    @Test
    fun `consumeCancelSuccess resets flag`() = runTest {
        val item = mockItem(status = Reservation.Status.CONFIRMED)
        val vm = ReservationDetailViewModel(
            item,
            FakeReservationsRepositoryDetail(cancelResult = Either.Right(Unit)),
        )
        vm.cancelReservation()
        advanceUntilIdle()
        vm.consumeCancelSuccess()
        assertFalse(vm.uiState.value.cancelSuccess)
    }
}

private class FakeReservationsRepositoryDetail(
    private val paymentInfoResult: Either<RepositoryError, MobilePaymentInfo> = Either.Right(mockPaymentInfo()),
    private val cancelResult: Either<RepositoryError, Unit> = Either.Right(Unit),
) : ReservationsRepository {
    override suspend fun getMyReservations() = Either.Right(emptyList<MyReservationListItem>())
    override suspend fun getPaymentInfo(id: Uuid) = paymentInfoResult
    override suspend fun cancelReservation(id: Uuid) = cancelResult
    override suspend fun createInstanceReservation(request: CreateInstanceReservationRequest) =
        throw UnsupportedOperationException()
    override suspend fun createSeriesReservation(request: CreateSeriesReservationRequest) =
        throw UnsupportedOperationException()
}

private fun mockItem(
    status: Reservation.Status = Reservation.Status.CONFIRMED,
    paymentType: PaymentInfo.Type = PaymentInfo.Type.BANK_TRANSFER,
) = MyReservationListItem(
    id = Uuid.parse("00000000-0000-0000-0000-000000000002"),
    eventTitle = "Pilates",
    startDateTime = LocalDateTime(2026, 8, 10, 10, 0),
    seatCount = 1,
    totalPrice = 300.0,
    status = status,
    paymentType = paymentType,
    variableSymbol = "9876543210",
    isSeries = false,
)

private fun mockPaymentInfo() = MobilePaymentInfo(
    spayd = "SPD*1.0*ACC:123456789/0800*AM:300.0*CC:CZK*X-VS:9876543210*MSG:Rezervace",
    amount = 300.0,
    variableSymbol = "9876543210",
    iban = "CZ1208000000001234567890",
    accountNumber = "123456789/0800",
)
