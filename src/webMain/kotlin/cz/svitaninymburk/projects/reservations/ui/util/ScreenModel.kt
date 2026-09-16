package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.i18n.AppStrings
import cz.svitaninymburk.projects.reservations.i18n.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Základ pro screen state holdery. Drží toast a jednotně zabaluje opakovaný
 * idiom "launch → loading flag → onRight/onLeft → toast".
 */
abstract class ScreenModel(protected val scope: CoroutineScope) {

    var toast: ToastData? by mutableStateOf(null)
        private set

    protected val currentStrings: AppStrings get() = strings.value

    /** Synchronní fold výsledku na toast (Left) / onSuccess (Right). */
    protected fun <L, R> handle(
        result: Either<L, R>,
        errorMessage: (L) -> String,
        onSuccess: (R) -> Unit,
    ) {
        result
            .onRight(onSuccess)
            .onLeft { toast = ToastData(errorMessage(it), ToastType.Error) }
    }

    /**
     * Spustí suspend blok ve scope, přepíná loading a foldne výsledek na toast / [onSuccess].
     *
     * [onError] umí chybu odchytit dřív, než se z ní stane toast — vrátí `true`, když
     * si ji vzal na starost (typicky ji ukáže jako dotaz v modálu). Bez něj platí
     * obvyklé chování, tedy toast přes [errorMessage].
     */
    protected fun <L, R> run(
        loading: ((Boolean) -> Unit)? = null,
        errorMessage: (L) -> String,
        block: suspend () -> Either<L, R>,
        onSuccess: (R) -> Unit = {},
        onError: (L) -> Boolean = { false },
    ): Job = scope.launch {
        loading?.invoke(true)
        try {
            block()
                .onRight(onSuccess)
                .onLeft { if (!onError(it)) showToast(errorMessage(it), ToastType.Error) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            showToast(e.message ?: "Error", ToastType.Error)
        } finally {
            loading?.invoke(false)
        }
    }

    protected fun showToast(message: String, type: ToastType = ToastType.Success) {
        toast = ToastData(message, type)
    }

    fun dismissToast() {
        toast = null
    }
}
