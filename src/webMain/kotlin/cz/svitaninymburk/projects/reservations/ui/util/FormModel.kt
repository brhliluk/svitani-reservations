package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Základ pro formuláře, které chybu ukazují u sebe, ne toastem — dialog nebo
 * karta zůstává otevřená a uživatel musí u pole vidět, co je špatně.
 * `ScreenModel.run` sype chyby do toastu, proto tady vlastní [submit].
 *
 * Zabaluje i to, co si každý takový formulář opisoval: loading příznak,
 * vynulování předchozí chyby a záchytný `catch` na cokoli, co RPC nepředpokládá.
 * Používají to přihlašovací dialogy (`ui/auth`) i vyhledání peněženky
 * (`ui/wallet`).
 */
abstract class FormModel(scope: CoroutineScope) : ScreenModel(scope) {

    var isLoading by mutableStateOf(false); private set
    var errorMessage: String? by mutableStateOf(null)
        private set

    /**
     * [localize] se předává, protože `localizedMessage` je definované na každém
     * konkrétním typu chyby zvlášť, ne na [AppError] — obecné `E` ho tedy nemá.
     * Stejně to řeší `ScreenModel.run`.
     */
    protected fun <E, R> submit(
        localize: (E) -> String,
        block: suspend () -> Either<E, R>,
        onSuccess: (R) -> Unit,
        onFailure: (String) -> Unit = { fail(it) },
    ) {
        if (isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                block()
                    .onRight(onSuccess)
                    .onLeft { onFailure(localize(it)) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onFailure(currentStrings.errorProcessingRequest)
            } finally {
                isLoading = false
            }
        }
    }

    protected fun fail(message: String) { errorMessage = message }
}
