package cz.svitaninymburk.projects.reservations.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonItem
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.components.usecase.LessonOptOutMutations
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Stav odhlašovacího dialogu. Drží ho model, ne composable — sekce lekcí visí na
 * dvou různých obrazovkách a obě by jinak měly vlastní kopii téhle logiky.
 */
class LessonOptOutModel(
    scope: CoroutineScope,
    private val reservationId: Uuid,
    private val mutations: LessonOptOutMutations,
) : ScreenModel(scope) {

    var pendingLesson: SeriesLessonItem? by mutableStateOf(null); private set
    var walletCode by mutableStateOf(""); private set
    var showEmailMismatchWarning by mutableStateOf(false); private set
    var isSubmitting by mutableStateOf(false); private set
    var errorMessage: String? by mutableStateOf(null); private set

    fun startOptOut(lesson: SeriesLessonItem) {
        pendingLesson = lesson
        walletCode = ""
        showEmailMismatchWarning = false
        errorMessage = null
    }

    fun setWalletCode(value: String) {
        walletCode = value
    }

    fun dismiss() {
        pendingLesson = null
        walletCode = ""
        showEmailMismatchWarning = false
        errorMessage = null
    }

    /**
     * Potvrzení omluvenky. Neshoda e-mailu u peněženky není chyba k zobrazení, ale
     * dotaz — server v tom případě nic nezapsal, takže se dá rovnou potvrdit znovu
     * s [force].
     */
    fun confirm(force: Boolean, onDone: () -> Unit) {
        val lesson = pendingLesson ?: return
        if (isSubmitting) return

        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                mutations.optOut(reservationId, lesson.instanceId, walletCode.ifBlank { null }, force)
                    .onRight { result ->
                        dismiss()
                        val credit = result.walletCreditAmount
                        val code = result.walletCode
                        // Kód peněženky je pro člověka bez účtu jediná cesta ke kreditu,
                        // takže se musí objevit rovnou, ne jen v e-mailu.
                        if (credit != null && credit > 0.0 && code != null) {
                            showToast("${currentStrings.walletCreditIssued}: $code", ToastType.Success)
                        } else {
                            showToast(currentStrings.toastLessonOptOut, ToastType.Success)
                        }
                        onDone()
                    }
                    .onLeft { error ->
                        if (error is ReservationError.WalletEmailMismatch) {
                            showEmailMismatchWarning = true
                        } else {
                            errorMessage = error.localizedMessage(currentStrings)
                        }
                    }
            } finally {
                isSubmitting = false
            }
        }
    }
}

fun IComponent.buildLessonOptOutModel(scope: CoroutineScope, reservationId: Uuid): LessonOptOutModel {
    val service = getService<ReservationServiceInterface>(RpcSerializersModules)
    return LessonOptOutModel(
        scope = scope,
        reservationId = reservationId,
        mutations = LessonOptOutMutations(service),
    )
}
