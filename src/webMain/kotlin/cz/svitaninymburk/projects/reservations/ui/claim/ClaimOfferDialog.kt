package cz.svitaninymburk.projects.reservations.ui.claim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.SessionModel
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import dev.kilua.core.IComponent
import dev.kilua.html.p

/**
 * Nabídka hned po přihlášení nebo registraci: „na váš e-mail máme rezervace, chcete je
 * přidat k účtu?“ Potvrzením si uživatel nechá poslat odkaz, samotné přidání proběhne
 * až po kliknutí v mailu.
 *
 * Ukazuje jen počet — výpis rezervací je až v mailu, který chodí majiteli schránky.
 */
@Composable
fun IComponent.ClaimOfferDialog(session: SessionModel) {
    if (!session.showsClaimOffer) return
    val currentStrings by strings

    ConfirmModal(
        title = currentStrings.claimOfferTitle,
        confirmLabel = currentStrings.claimOfferConfirm,
        dismissLabel = currentStrings.claimOfferLater,
        isLoading = session.isSendingClaimLink,
        onConfirm = { session.sendClaimLink() },
        onDismiss = { session.dismissClaimOffer() },
        confirmClassName = "btn-primary",
    ) {
        p(className = "py-2") { +currentStrings.claimOfferBody(session.claimableCount) }
        p(className = "text-xs text-base-content/60") { +currentStrings.claimOfferHint }
    }
}
