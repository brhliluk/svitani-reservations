package cz.svitaninymburk.projects.reservations.android.repository.event

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.api.ApiError
import cz.svitaninymburk.projects.reservations.api.EventsResponse
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.event.EventInstance
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlin.uuid.Uuid

class EventsRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : EventsRepository {
    private val accessToken: String?
        get() = prefs.getString("access_token", null)

    override suspend fun getEvents(): Either<RepositoryError, EventsResponse> =
        getJson("/api/v1/events")

    override suspend fun getInstance(id: Uuid): Either<RepositoryError, EventInstance> =
        getJson("/api/v1/events/instances/$id")

    override suspend fun getSeriesDetail(id: Uuid): Either<RepositoryError, SeriesDetailResponse> =
        getJson("/api/v1/events/series/$id")

    private suspend inline fun <reified T> getJson(path: String): Either<RepositoryError, T> = either {
        ensure(accessToken != null) { RepositoryError.Unauthorized }
        val response: HttpResponse = catch {
            httpClient.get(path) {
                bearerAuth(accessToken!!)
            }
        }.mapLeft { RepositoryError.Network }.bind()

        ensure(response.status.isSuccess()) {
            catch { response.body<ApiError>() }
                .map { RepositoryError.Server(it.code, it.message) }
                .getOrElse { RepositoryError.Http(response.status.value) }
        }

        catch { response.body<T>() }
            .mapLeft { RepositoryError.Parse }
            .bind()
    }
}
