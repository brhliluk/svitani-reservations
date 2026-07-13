package cz.svitaninymburk.projects.reservations.android.repository.wallet

import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthLocalDataSource
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.authGet
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import io.ktor.client.HttpClient

class WalletRepositoryImpl(
    private val httpClient: HttpClient,
    private val dataSource: AuthLocalDataSource,
) : WalletRepository {

    override suspend fun getMyWallet(): Either<RepositoryError, WalletInfo> =
        httpClient.authGet("/api/v1/wallet", dataSource.getAccessToken())
}
