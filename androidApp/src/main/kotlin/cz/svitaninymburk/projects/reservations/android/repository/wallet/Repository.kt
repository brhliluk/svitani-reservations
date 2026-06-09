package cz.svitaninymburk.projects.reservations.android.repository.wallet

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.api.ApiError
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.isSuccess

class WalletRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : WalletRepository {
    private val accessToken: String?
        get() = prefs.getString("access_token", null)

    override suspend fun getMyWallet(): Either<RepositoryError, WalletInfo> = either {
        ensure(accessToken != null) { RepositoryError.Unauthorized }
        val response = catch {
            httpClient.get("/api/v1/wallet") {
                bearerAuth(accessToken!!)
            }
        }.mapLeft { RepositoryError.Network }.bind()

        ensure(response.status.isSuccess()) {
            catch { response.body<ApiError>() }
                .map { RepositoryError.Server(it.code, it.message) }
                .getOrElse { RepositoryError.Http(response.status.value) }
        }

        catch { response.body<WalletInfo>() }
            .mapLeft { RepositoryError.Parse }
            .bind()
    }
}
