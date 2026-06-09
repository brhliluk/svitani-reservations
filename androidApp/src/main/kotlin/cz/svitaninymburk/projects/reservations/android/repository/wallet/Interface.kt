package cz.svitaninymburk.projects.reservations.android.repository.wallet

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo

interface WalletRepository {
    suspend fun getMyWallet(): Either<RepositoryError, WalletInfo>
}
