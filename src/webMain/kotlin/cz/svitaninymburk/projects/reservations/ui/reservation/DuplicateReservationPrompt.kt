package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.error.DuplicateScope
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import dev.kilua.core.IComponent
import dev.kilua.html.p

/**
 * Server první pokus o rezervaci na akci, kterou už stejný e-mail má, odmítne —
 * nic nezaloží ani nezabere místo. Tenhle držák si odložené odeslání pamatuje,
 * dokud se uživatel nerozhodne, jestli chce rezervovat i tak.
 *
 * Stejný vzor jako `force` u rušení rezervace: varování není chyba, ale dotaz.
 */
class DuplicateReservationPrompt {

    data class Pending(
        val scope: DuplicateScope,
        val target: ReservationTarget,
        val form: ReservationFormData,
    )

    var pending: Pending? by mutableStateOf(null); private set

    fun show(scope: DuplicateScope, target: ReservationTarget, form: ReservationFormData) {
        pending = Pending(scope, target, form)
    }

    fun dismiss() { pending = null }

    /** Vydá odložené odeslání a zavře modál — volá se při potvrzení. */
    fun take(): Pending? = pending.also { pending = null }
}

@Composable
fun IComponent.DuplicateReservationModal(
    pending: DuplicateReservationPrompt.Pending,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentStrings by strings

    ConfirmModal(
        title = currentStrings.duplicateReservationTitle,
        confirmLabel = currentStrings.duplicateReservationConfirm,
        dismissLabel = currentStrings.modalBack,
        isLoading = isLoading,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        titleClassName = "text-warning",
        confirmClassName = "btn-warning",
    ) {
        // Tytéž hlášky, které by se jinak ukázaly jako chyba — ať se formulace nerozejdou.
        p(className = "py-4") { +pending.scope.localizedMessage(currentStrings) }
    }
}
