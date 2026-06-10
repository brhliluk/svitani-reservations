package cz.svitaninymburk.projects.reservations.android.feature.events

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.feature.events.detail.InstanceDetailViewModel
import cz.svitaninymburk.projects.reservations.android.feature.events.detail.SeriesDetailViewModel
import cz.svitaninymburk.projects.reservations.android.repository.event.EventsRepository
import cz.svitaninymburk.projects.reservations.api.EventsResponse
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlin.test.*
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelsTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repo(
        instanceResult: Either<RepositoryError, EventInstance> = Either.Right(mockInstance()),
        seriesResult: Either<RepositoryError, SeriesDetailResponse> = Either.Right(SeriesDetailResponse(mockSeries(), emptyList())),
    ) = object : EventsRepository {
        var instanceCalls = 0
        override suspend fun getEvents() = Either.Right(EventsResponse(emptyList(), emptyList()))
        override suspend fun getInstance(id: Uuid): Either<RepositoryError, EventInstance> {
            instanceCalls++
            return instanceResult
        }
        override suspend fun getSeriesDetail(id: Uuid) = seriesResult
    }

    @Test
    fun `instance detail loads on init`() = runTest {
        val instance = mockInstance()
        val vm = InstanceDetailViewModel(instance.id, repo(instanceResult = Either.Right(instance)))
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals(instance, vm.uiState.value.instance)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `instance detail failure sets error`() = runTest {
        val vm = InstanceDetailViewModel(Uuid.random(), repo(instanceResult = Either.Left(RepositoryError.Network)))
        advanceUntilIdle()
        assertEquals(RepositoryError.Network, vm.uiState.value.error)
        assertNull(vm.uiState.value.instance)
    }

    @Test
    fun `instance detail refresh re-fetches`() = runTest {
        val r = repo()
        val vm = InstanceDetailViewModel(Uuid.random(), r)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, r.instanceCalls)
    }

    @Test
    fun `series detail loads on init`() = runTest {
        val series = mockSeries()
        val lessons = listOf(mockInstance(seriesId = series.id))
        val vm = SeriesDetailViewModel(series.id, repo(seriesResult = Either.Right(SeriesDetailResponse(series, lessons))))
        advanceUntilIdle()
        assertEquals(series, vm.uiState.value.detail?.series)
        assertEquals(lessons, vm.uiState.value.detail?.lessons)
    }

    @Test
    fun `series detail failure sets error`() = runTest {
        val vm = SeriesDetailViewModel(Uuid.random(), repo(seriesResult = Either.Left(RepositoryError.Parse)))
        advanceUntilIdle()
        assertEquals(RepositoryError.Parse, vm.uiState.value.error)
        assertNull(vm.uiState.value.detail)
    }
}
