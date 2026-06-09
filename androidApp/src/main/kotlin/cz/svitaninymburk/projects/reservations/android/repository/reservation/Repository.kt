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
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import kotlin.uuid.Uuid

class ReservationsRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : ReservationsRepository {
    private val accessToken: String?
        get() = prefs.getString("access_token", null)

    override suspend fun getMyReservations(): Either<RepositoryError, List<MyReservationListItem>> = either {
        ensure(accessToken != null) { RepositoryError.Unauthorized }
        val response = catch {
            httpClient.get("/api/v1/reservations/mine") {
                bearerAuth(accessToken!!)
            }
        }.mapLeft { RepositoryError.Network }.bind()

        ensure(response.status.isSuccess()) {
            catch { response.body<ApiError>() }
                .map { RepositoryError.Server(it.code, it.message) }
                .getOrElse { RepositoryError.Http(response.status.value) }
        }

        catch { response.body<List<MyReservationListItem>>() }
            .mapLeft { RepositoryError.Parse }
            .bind()
    }

    override suspend fun getPaymentInfo(id: Uuid): Either<RepositoryError, MobilePaymentInfo> = either {
        ensure(accessToken != null) { RepositoryError.Unauthorized }
        val response = catch {
            httpClient.get("/api/v1/reservations/$id/payment") {
                bearerAuth(accessToken!!)
            }
        }.mapLeft { RepositoryError.Network }.bind()

        ensure(response.status.isSuccess()) {
            catch { response.body<ApiError>() }
                .map { RepositoryError.Server(it.code, it.message) }
                .getOrElse { RepositoryError.Http(response.status.value) }
        }

        catch { response.body<MobilePaymentInfo>() }
            .mapLeft { RepositoryError.Parse }
            .bind()
    }

    override suspend fun cancelReservation(id: Uuid): Either<RepositoryError, Unit> = either {
        ensure(accessToken != null) { RepositoryError.Unauthorized }
        val response = catch {
            httpClient.post("/api/v1/reservations/$id/cancel") {
                bearerAuth(accessToken!!)
            }
        }.mapLeft { RepositoryError.Network }.bind()

        ensure(response.status.isSuccess()) {
            catch { response.body<ApiError>() }
                .map { RepositoryError.Server(it.code, it.message) }
                .getOrElse { RepositoryError.Http(response.status.value) }
        }
    }
}
