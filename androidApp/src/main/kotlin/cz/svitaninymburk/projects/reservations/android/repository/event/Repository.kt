package cz.svitaninymburk.projects.reservations.android.repository.event

import android.content.SharedPreferences
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.accessToken
import cz.svitaninymburk.projects.reservations.android.repository.authGet
import cz.svitaninymburk.projects.reservations.api.EventsResponse
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.event.EventInstance
import io.ktor.client.HttpClient
import kotlin.uuid.Uuid

class EventsRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : EventsRepository {

    override suspend fun getEvents(): Either<RepositoryError, EventsResponse> =
        httpClient.authGet("/api/v1/events", prefs.accessToken)

    override suspend fun getInstance(id: Uuid): Either<RepositoryError, EventInstance> =
        httpClient.authGet("/api/v1/events/instances/$id", prefs.accessToken)

    override suspend fun getSeriesDetail(id: Uuid): Either<RepositoryError, SeriesDetailResponse> =
        httpClient.authGet("/api/v1/events/series/$id", prefs.accessToken)
}
