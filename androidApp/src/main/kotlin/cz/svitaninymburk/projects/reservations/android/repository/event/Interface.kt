package cz.svitaninymburk.projects.reservations.android.repository.event

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.api.EventsResponse
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlin.uuid.Uuid

interface EventsRepository {
    suspend fun getEvents(): Either<RepositoryError, EventsResponse>
    suspend fun getInstance(id: Uuid): Either<RepositoryError, EventInstance>
    suspend fun getSeriesDetail(id: Uuid): Either<RepositoryError, SeriesDetailResponse>
}
