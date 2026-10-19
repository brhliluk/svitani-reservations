package cz.svitaninymburk.projects.reservations.ui.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.FormModel
import cz.svitaninymburk.projects.reservations.ui.wallet.usecase.WalletLookupQueries
import cz.svitaninymburk.projects.reservations.ui.wallet.usecase.canLookUpWallet
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope

class WalletLookupModel(
    scope: CoroutineScope,
    private val queries: WalletLookupQueries,
    initialCode: String,
    initialEmail: String,
) : FormModel(scope) {

    var code by mutableStateOf(initialCode); private set
    var email by mutableStateOf(initialEmail); private set
    var walletInfo: WalletInfo? by mutableStateOf(null); private set

    val canLookUp: Boolean get() = canLookUpWallet(code, email)

    fun setCode(value: String) { code = value }
    fun setEmail(value: String) { email = value }

    fun lookUp() {
        if (!canLookUp) return
        // Předchozí zůstatek zmizí hned, aby u nového dotazu nesvítil starý.
        walletInfo = null
        submit(
            localize = { it.localizedMessage(currentStrings) },
            block = { queries.info(code, email) },
            onSuccess = { walletInfo = it },
        )
    }
}

fun IComponent.buildWalletLookupModel(
    scope: CoroutineScope,
    initialCode: String,
    initialEmail: String,
): WalletLookupModel = WalletLookupModel(
    scope = scope,
    queries = WalletLookupQueries(getService<ReservationServiceInterface>(RpcSerializersModules)),
    initialCode = initialCode,
    initialEmail = initialEmail,
)
