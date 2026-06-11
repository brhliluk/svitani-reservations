package cz.svitaninymburk.projects.reservations.android.repository.reservation

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.api.ApiError
import cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.uuid.Uuid

class ReservationsRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : ReservationsRepository {
    private val accessToken: String?
        get() = prefs.getString("access_token", null)

    override suspend fun getMyReservations(): Either<RepositoryError, List<MyReservationListItem>> =
        getJson("/api/v1/reservations/mine")

    override suspend fun getPaymentInfo(id: Uuid): Either<RepositoryError, MobilePaymentInfo> =
        getJson("/api/v1/reservations/$id/payment")

    override suspend fun cancelReservation(id: Uuid): Either<RepositoryError, Unit> =
        postNoBody("/api/v1/reservations/$id/cancel")

    override suspend fun createInstanceReservation(
        request: CreateInstanceReservationRequest,
    ): Either<RepositoryError, Reservation> =
        postJson("/api/v1/reservations/instance", request)

    override suspend fun createSeriesReservation(
        request: CreateSeriesReservationRequest,
    ): Either<RepositoryError, Reservation> =
        postJson("/api/v1/reservations/series", request)

    private suspend inline fun <reified T> getJson(path: String): Either<RepositoryError, T> = either {
        ensure(accessToken != null) { RepositoryError.Unauthorized }
        val response = catch {
            httpClient.get(path) { bearerAuth(accessToken!!) }
        }.mapLeft { RepositoryError.Network }.bind()

        ensure(response.status.isSuccess()) { errorFrom(response) }

        catch { response.body<T>() }
            .mapLeft { RepositoryError.Parse }
            .bind()
    }

    private suspend inline fun <reified B, reified T> postJson(
        path: String,
        body: B,
    ): Either<RepositoryError, T> = either {
        ensure(accessToken != null) { RepositoryError.Unauthorized }
        val response = catch {
            httpClient.post(path) {
                bearerAuth(accessToken!!)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.mapLeft { RepositoryError.Network }.bind()

        ensure(response.status.isSuccess()) { errorFrom(response) }

        catch { response.body<T>() }
            .mapLeft { RepositoryError.Parse }
            .bind()
    }

    private suspend fun postNoBody(path: String): Either<RepositoryError, Unit> = either {
        ensure(accessToken != null) { RepositoryError.Unauthorized }
        val response = catch {
            httpClient.post(path) { bearerAuth(accessToken!!) }
        }.mapLeft { RepositoryError.Network }.bind()

        ensure(response.status.isSuccess()) { errorFrom(response) }
    }

    /** Maps a failed HTTP response to a Server error (from the ApiError body) or a bare Http error. */
    private suspend fun errorFrom(response: HttpResponse): RepositoryError =
        catch { response.body<ApiError>() }
            .map { RepositoryError.Server(it.code, it.message) }
            .getOrElse { RepositoryError.Http(response.status.value) }
}
