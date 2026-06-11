package cz.svitaninymburk.projects.reservations.android.repository.wallet

import android.content.SharedPreferences
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.accessToken
import cz.svitaninymburk.projects.reservations.android.repository.authGet
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import io.ktor.client.HttpClient

class WalletRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences,
) : WalletRepository {

    override suspend fun getMyWallet(): Either<RepositoryError, WalletInfo> =
        httpClient.authGet("/api/v1/wallet", prefs.accessToken)
}
