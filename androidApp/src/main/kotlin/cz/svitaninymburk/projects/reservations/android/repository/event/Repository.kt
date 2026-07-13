package cz.svitaninymburk.projects.reservations.android.repository.event

import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthLocalDataSource
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.authGet
import cz.svitaninymburk.projects.reservations.api.EventsResponse
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.event.EventInstance
import io.ktor.client.HttpClient
import kotlin.uuid.Uuid

class EventsRepositoryImpl(
    private val httpClient: HttpClient,
    private val dataSource: AuthLocalDataSource,
) : EventsRepository {

    override suspend fun getEvents(): Either<RepositoryError, EventsResponse> =
        httpClient.authGet("/api/v1/events", dataSource.getAccessToken())

    override suspend fun getInstance(id: Uuid): Either<RepositoryError, EventInstance> =
        httpClient.authGet("/api/v1/events/instances/$id", dataSource.getAccessToken())

    override suspend fun getSeriesDetail(id: Uuid): Either<RepositoryError, SeriesDetailResponse> =
        httpClient.authGet("/api/v1/events/series/$id", dataSource.getAccessToken())
}
