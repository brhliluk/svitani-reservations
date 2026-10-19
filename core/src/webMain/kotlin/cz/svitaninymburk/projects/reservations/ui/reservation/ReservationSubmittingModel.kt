package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitter
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import web.console.console
import kotlin.uuid.Uuid

/**
 * Odeslání rezervace z veřejného rozcestníku i z admin náhledu akce — obě
 * obrazovky měly vlastní kopii téhle smyčky.
 *
 * [isSubmitting] se **musí** nulovat ve `finally`: Kilua RPC klient při chybě
 * (přerušené spojení, chybová obálka ze serveru) vyhazuje výjimku, nevrací
 * `Either.Left`, takže řádek za suspend voláním je nedosažitelný. Bez `finally`
 * zůstal spinner viset navždy, bez hlášky, a uživatel odeslal rezervaci znovu —
 * právě tak vznikly duplicity 18. 8. 2026.
 *
 * Výjimka taky **neznamená neúspěch**: rezervace mohla na serveru vzniknout,
 * proto hláška "nevíme, jak to dopadlo", ne "nepodařilo se".
 */
abstract class ReservationSubmittingModel(
    scope: CoroutineScope,
    private val submitter: ReservationSubmitter,
    private val onReserved: (Reservation) -> Unit,
) : ScreenModel(scope) {

    var isSubmitting by mutableStateOf(false); private set

    /** Varování „na tuhle akci už rezervaci máte“ — dotaz, ne chyba. */
    val duplicatePrompt = DuplicateReservationPrompt()

    /** Zapamatováno kvůli opakovanému odeslání po potvrzení varování. */
    private var userId: Uuid? = null

    /** Dashboard chybu ještě zabaluje do "rezervace se nezdařila", náhled ji ukazuje holou. */
    protected open fun reservationErrorMessage(localized: String): String = localized

    fun submitReservation(
        target: ReservationTarget,
        formData: ReservationFormData,
        userId: Uuid?,
        acknowledgedDuplicate: Boolean = false,
    ) {
        this.userId = userId
        scope.launch {
            isSubmitting = true
            try {
                submitter.submit(target, formData, userId, acknowledgedDuplicate)
                    .onRight { onReserved(it) }
                    .onLeft { error ->
                        if (error is ReservationError.AlreadyReserved) {
                            duplicatePrompt.show(error.scope, target, formData)
                        } else {
                            showToast(reservationErrorMessage(error.localizedMessage(currentStrings)), ToastType.Error)
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                console.error("Reservation submit failed: ${e.message}")
                showToast(currentStrings.reservationOutcomeUnknown, ToastType.Error)
            } finally {
                isSubmitting = false
            }
        }
    }

    /** Uživatel varování o duplicitě odklikl — pošleme tentýž formulář znovu, už s příznakem. */
    fun confirmDuplicate() {
        val pending = duplicatePrompt.take() ?: return
        submitReservation(pending.target, pending.form, userId, acknowledgedDuplicate = true)
    }

    fun dismissDuplicate() = duplicatePrompt.dismiss()
}
