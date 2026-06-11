package cz.svitaninymburk.projects.reservations.android.feature.events

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.feature.events.reservation.ReservationFormViewModel
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthRepository
import cz.svitaninymburk.projects.reservations.android.repository.event.EventsRepository
import cz.svitaninymburk.projects.reservations.android.repository.reservation.ReservationsRepository
import cz.svitaninymburk.projects.reservations.android.repository.wallet.WalletRepository
import cz.svitaninymburk.projects.reservations.api.EventsResponse
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.auth.UserDto
import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.event.PriceModifier
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val instanceId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val seriesId = Uuid.parse("00000000-0000-0000-0000-000000000003")
    private val definitionId = Uuid.parse("00000000-0000-0000-0000-000000000004")

    private fun mockInstance(
        price: Double = 300.0,
        capacity: Int = 10,
        occupiedSpots: Int = 7,
        customFields: List<cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition> = emptyList(),
        allowedPaymentTypes: List<PaymentInfo.Type> = listOf(PaymentInfo.Type.BANK_TRANSFER, PaymentInfo.Type.ON_SITE),
    ) = EventInstance(
        id = instanceId,
        definitionId = definitionId,
        title = "Pilates",
        description = "",
        startDateTime = LocalDateTime(2026, 8, 10, 10, 0),
        endDateTime = LocalDateTime(2026, 8, 10, 12, 0),
        price = price,
        capacity = capacity,
        occupiedSpots = occupiedSpots,
        customFields = customFields,
        allowedPaymentTypes = allowedPaymentTypes,
    )

    private fun mockSeries() = EventSeries(
        id = seriesId,
        definitionId = definitionId,
        title = "Kurz keramiky",
        description = "",
        price = 1500.0,
        capacity = 8,
        occupiedSpots = 2,
        startDate = LocalDate(2026, 9, 1),
        endDate = LocalDate(2026, 12, 15),
        lessonCount = 10,
    )

    private fun mockUser() = UserDto(
        id = Uuid.parse("00000000-0000-0000-0000-000000000005"),
        email = "jan@example.com",
        fullName = "Jan Novák",
        role = User.Role.USER,
    )

    private fun mockReservation() = Reservation(
        id = Uuid.parse("00000000-0000-0000-0000-000000000002"),
        reference = Reference.Instance(instanceId),
        contactName = "Jan Novák",
        contactEmail = "jan@example.com",
        contactPhone = "+420123456789",
        seatCount = 2,
        totalPrice = 600.0,
        status = Reservation.Status.PENDING_PAYMENT,
        createdAt = Instant.parse("2026-06-10T10:00:00Z"),
        customValues = emptyMap(),
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
        variableSymbol = "1234567890",
    )

    private fun viewModel(
        isSeries: Boolean = false,
        instance: EventInstance = mockInstance(),
        series: EventSeries = mockSeries(),
        reservations: FakeReservationsRepository = FakeReservationsRepository(Either.Right(mockReservation())),
        wallet: Either<RepositoryError, WalletInfo> = Either.Left(RepositoryError.Http(404)),
        user: UserDto? = mockUser(),
    ) = ReservationFormViewModel(
        id = if (isSeries) seriesId else instanceId,
        isSeries = isSeries,
        eventsRepository = FormFakeEventsRepository(
            instanceResult = Either.Right(instance),
            seriesResult = Either.Right(SeriesDetailResponse(series, emptyList())),
        ),
        reservationsRepository = reservations,
        walletRepository = FakeWalletRepository(wallet),
        authRepository = FakeAuthRepository(user),
        locale = "cs",
    )

    // --- Task 2: načtení, prefill, wallet ---

    @Test
    fun `loads instance target and prefills contact from stored user`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertIs<ReservationTarget.Instance>(state.target)
        assertEquals("Pilates", state.target?.title)
        assertEquals("Jan Novák", state.contactName)
        assertEquals("jan@example.com", state.contactEmail)
        assertEquals(PaymentInfo.Type.BANK_TRANSFER, state.paymentType)
    }

    @Test
    fun `loads series target when isSeries`() = runTest {
        val vm = viewModel(isSeries = true)
        advanceUntilIdle()
        assertIs<ReservationTarget.Series>(vm.uiState.value.target)
        assertEquals("Kurz keramiky", vm.uiState.value.target?.title)
    }

    @Test
    fun `load error is exposed`() = runTest {
        val vm = ReservationFormViewModel(
            id = instanceId,
            isSeries = false,
            eventsRepository = FormFakeEventsRepository(instanceResult = Either.Left(RepositoryError.Network)),
            reservationsRepository = FakeReservationsRepository(Either.Right(mockReservation())),
            walletRepository = FakeWalletRepository(Either.Left(RepositoryError.Http(404))),
            authRepository = FakeAuthRepository(mockUser()),
            locale = "cs",
        )
        advanceUntilIdle()
        assertEquals(RepositoryError.Network, vm.uiState.value.loadError)
    }

    @Test
    fun `wallet with positive balance is exposed`() = runTest {
        val vm = viewModel(wallet = Either.Right(walletInfo(balance = 250.0)))
        advanceUntilIdle()
        assertEquals(250.0, vm.uiState.value.wallet?.balance)
    }

    @Test
    fun `wallet with zero balance is ignored`() = runTest {
        val vm = viewModel(wallet = Either.Right(walletInfo(balance = 0.0)))
        advanceUntilIdle()
        assertNull(vm.uiState.value.wallet)
    }

    @Test
    fun `wallet error is silently ignored`() = runTest {
        val vm = viewModel(wallet = Either.Left(RepositoryError.Http(404)))
        advanceUntilIdle()
        assertNull(vm.uiState.value.wallet)
        assertNull(vm.uiState.value.loadError)
    }
}

private fun walletInfo(balance: Double) = WalletInfo(
    code = "WAL12345678901",
    balance = balance,
    emailMatches = true,
    seasonResetDay = 1,
    seasonResetMonth = 9,
)

private class FormFakeEventsRepository(
    private val instanceResult: Either<RepositoryError, EventInstance>? = null,
    private val seriesResult: Either<RepositoryError, SeriesDetailResponse>? = null,
) : EventsRepository {
    override suspend fun getEvents(): Either<RepositoryError, EventsResponse> =
        throw UnsupportedOperationException()
    override suspend fun getInstance(id: Uuid) = instanceResult!!
    override suspend fun getSeriesDetail(id: Uuid) = seriesResult!!
}

private class FakeReservationsRepository(
    private val createResult: Either<RepositoryError, Reservation>,
) : ReservationsRepository {
    var lastInstanceRequest: CreateInstanceReservationRequest? = null
    var lastSeriesRequest: CreateSeriesReservationRequest? = null
    override suspend fun getMyReservations() = Either.Right(emptyList<MyReservationListItem>())
    override suspend fun getPaymentInfo(id: Uuid): Either<RepositoryError, cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo> = throw UnsupportedOperationException()
    override suspend fun cancelReservation(id: Uuid) = Either.Right(Unit)
    override suspend fun createInstanceReservation(request: CreateInstanceReservationRequest): Either<RepositoryError, Reservation> {
        lastInstanceRequest = request
        return createResult
    }
    override suspend fun createSeriesReservation(request: CreateSeriesReservationRequest): Either<RepositoryError, Reservation> {
        lastSeriesRequest = request
        return createResult
    }
}

private class FakeWalletRepository(
    private val result: Either<RepositoryError, WalletInfo>,
) : WalletRepository {
    override suspend fun getMyWallet() = result
}

private class FakeAuthRepository(private val user: UserDto?) : AuthRepository {
    override suspend fun login(email: String, password: String) = throw UnsupportedOperationException()
    override fun hasToken() = true
    override fun clearTokens() = Unit
    override fun getUser() = user
}
