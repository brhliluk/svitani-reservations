package cz.svitaninymburk.projects.reservations.android.feature.events

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.feature.events.list.EventsTab
import cz.svitaninymburk.projects.reservations.android.feature.events.list.EventsViewModel
import cz.svitaninymburk.projects.reservations.android.repository.event.EventsRepository
import cz.svitaninymburk.projects.reservations.api.EventsResponse
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {

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
    fun `initial state shows loading and triggers load`() = runTest {
        val vm = EventsViewModel(FakeEventsRepository())
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `filter cutoff uses Prague wall-clock time`() = runTest {
        // Fixed instant: 2099-07-15T07:30:00Z == 09:30 in Prague (CEST, UTC+2)
        val fixedClock = object : Clock {
            override fun now(): Instant = Instant.parse("2099-07-15T07:30:00Z")
        }
        val justPassedInPrague = mockInstance(start = LocalDateTime(2099, 7, 15, 9, 0), end = LocalDateTime(2099, 7, 15, 10, 0))
        val upcomingInPrague = mockInstance(start = LocalDateTime(2099, 7, 15, 10, 0), end = LocalDateTime(2099, 7, 15, 11, 0))
        val vm = EventsViewModel(
            FakeEventsRepository(
                eventsResult = Either.Right(EventsResponse(listOf(justPassedInPrague, upcomingInPrague), emptyList()))
            ),
            clock = fixedClock,
        )
        advanceUntilIdle()
        assertEquals(listOf(upcomingInPrague.id), vm.uiState.value.oneOff.map { it.id })
    }

    @Test
    fun `load failure sets error`() = runTest {
        val vm = EventsViewModel(FakeEventsRepository(eventsResult = Either.Left(RepositoryError.Network)))
        advanceUntilIdle()
        assertEquals(RepositoryError.Network, vm.uiState.value.error)
        assertTrue(vm.uiState.value.oneOff.isEmpty())
        assertTrue(vm.uiState.value.courses.isEmpty())
    }

    @Test
    fun `one-off tab contains standalone and drop-in instances only`() = runTest {
        val standalone = mockInstance()
        val dropIn = mockInstance(seriesId = Uuid.random(), isDropIn = true)
        val seriesLesson = mockInstance(seriesId = Uuid.random())
        val vm = EventsViewModel(
            FakeEventsRepository(
                eventsResult = Either.Right(EventsResponse(listOf(standalone, dropIn, seriesLesson), emptyList()))
            )
        )
        advanceUntilIdle()
        assertEquals(setOf(standalone.id, dropIn.id), vm.uiState.value.oneOff.map { it.id }.toSet())
    }

    @Test
    fun `past instances are hidden, cancelled future instances stay visible`() = runTest {
        val past = mockInstance(start = LocalDateTime(2020, 1, 1, 9, 0), end = LocalDateTime(2020, 1, 1, 10, 0))
        val cancelledFuture = mockInstance(isCancelled = true, start = LocalDateTime(2099, 7, 15, 9, 0), end = LocalDateTime(2099, 7, 15, 10, 0))
        val vm = EventsViewModel(
            FakeEventsRepository(
                eventsResult = Either.Right(EventsResponse(listOf(past, cancelledFuture), emptyList()))
            )
        )
        advanceUntilIdle()
        assertEquals(listOf(cancelledFuture.id), vm.uiState.value.oneOff.map { it.id })
    }

    @Test
    fun `one-off instances are sorted by start date`() = runTest {
        val later = mockInstance(start = LocalDateTime(2099, 8, 1, 9, 0), end = LocalDateTime(2099, 8, 1, 10, 0))
        val earlier = mockInstance(start = LocalDateTime(2099, 7, 1, 9, 0), end = LocalDateTime(2099, 7, 1, 10, 0))
        val vm = EventsViewModel(
            FakeEventsRepository(
                eventsResult = Either.Right(EventsResponse(listOf(later, earlier), emptyList()))
            )
        )
        advanceUntilIdle()
        assertEquals(listOf(earlier.id, later.id), vm.uiState.value.oneOff.map { it.id })
    }

    @Test
    fun `courses hide series after endDate and sort by startDate`() = runTest {
        val finished = mockSeries(startDate = LocalDate(2020, 1, 1), endDate = LocalDate(2020, 3, 1))
        val laterCourse = mockSeries(startDate = LocalDate(2099, 10, 1), endDate = LocalDate(2099, 12, 1))
        val earlierCourse = mockSeries(startDate = LocalDate(2099, 9, 1), endDate = LocalDate(2099, 11, 1))
        val vm = EventsViewModel(
            FakeEventsRepository(
                eventsResult = Either.Right(EventsResponse(emptyList(), listOf(finished, laterCourse, earlierCourse)))
            )
        )
        advanceUntilIdle()
        assertEquals(listOf(earlierCourse.id, laterCourse.id), vm.uiState.value.courses.map { it.id })
    }

    @Test
    fun `selectTab switches tab`() = runTest {
        val vm = EventsViewModel(FakeEventsRepository())
        advanceUntilIdle()
        assertEquals(EventsTab.ONE_OFF, vm.uiState.value.selectedTab)
        vm.selectTab(EventsTab.COURSES)
        assertEquals(EventsTab.COURSES, vm.uiState.value.selectedTab)
    }

    @Test
    fun `refresh re-fetches data`() = runTest {
        var callCount = 0
        val repo = object : EventsRepository {
            override suspend fun getEvents(): Either<RepositoryError, EventsResponse> {
                callCount++
                return Either.Right(EventsResponse(emptyList(), emptyList()))
            }
            override suspend fun getInstance(id: Uuid) = Either.Right(mockInstance())
            override suspend fun getSeriesDetail(id: Uuid) = Either.Right(SeriesDetailResponse(mockSeries(), emptyList()))
        }
        val vm = EventsViewModel(repo)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, callCount)
    }
}

private class FakeEventsRepository(
    private val eventsResult: Either<RepositoryError, EventsResponse> = Either.Right(EventsResponse(emptyList(), emptyList())),
) : EventsRepository {
    override suspend fun getEvents() = eventsResult
    override suspend fun getInstance(id: Uuid): Either<RepositoryError, EventInstance> = Either.Right(mockInstance())
    override suspend fun getSeriesDetail(id: Uuid): Either<RepositoryError, SeriesDetailResponse> =
        Either.Right(SeriesDetailResponse(mockSeries(), emptyList()))
}

internal fun mockInstance(
    id: Uuid = Uuid.random(),
    seriesId: Uuid? = null,
    isDropIn: Boolean = false,
    isCancelled: Boolean = false,
    start: LocalDateTime = LocalDateTime(2099, 7, 15, 9, 0),
    end: LocalDateTime = LocalDateTime(2099, 7, 15, 10, 0),
) = EventInstance(
    id = id,
    definitionId = Uuid.random(),
    seriesId = seriesId,
    title = "Jóga pro začátečníky",
    description = "Popis události",
    startDateTime = start,
    endDateTime = end,
    price = 200.0,
    capacity = 10,
    occupiedSpots = 3,
    isCancelled = isCancelled,
    isDropIn = isDropIn,
)

internal fun mockSeries(
    id: Uuid = Uuid.random(),
    startDate: LocalDate = LocalDate(2099, 9, 1),
    endDate: LocalDate = LocalDate(2099, 12, 15),
) = EventSeries(
    id = id,
    definitionId = Uuid.random(),
    title = "Kurz jógy",
    description = "Popis kurzu",
    price = 1800.0,
    capacity = 12,
    occupiedSpots = 5,
    startDate = startDate,
    endDate = endDate,
    lessonCount = 10,
)
