package cz.svitaninymburk.projects.reservations.ui.claim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h2
import dev.kilua.html.p
import dev.kilua.html.span

/**
 * Cíl potvrzovacího odkazu z mailu.
 *
 * Přidání spouští až tahle obrazovka, ne otevření URL: skenery odkazů v poštovních
 * klientech stránky preventivně načítají a odkaz by se uplatnil dřív, než na něj člověk
 * vůbec klikne.
 */
@Composable
fun IComponent.ClaimReservationsScreen(
    token: String,
    onOpenMyReservations: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember(token) { buildClaimReservationsModel(scope, token) }

    LaunchedEffect(token) { model.confirm() }

    div(className = "min-h-screen bg-base-200 flex items-center justify-center px-4") {
        div(className = "card w-full max-w-md shadow-2xl bg-base-100") {
            div(className = "card-body items-center text-center") {
                h2(className = "card-title") { +currentStrings.claimConfirmTitle }

                when (val state = model.state) {
                    is ClaimConfirmState.Loading -> {
                        span(className = "loading loading-spinner loading-lg my-4")
                        p(className = "text-base-content/60") { +currentStrings.claimConfirmLoading }
                    }

                    is ClaimConfirmState.Done -> {
                        p(className = "py-4") { +claimResultMessage(state.result, currentStrings) }
                        button(className = "btn btn-primary w-full") {
                            onClick { onOpenMyReservations() }
                            +currentStrings.claimConfirmOpenMyReservations
                        }
                    }

                    is ClaimConfirmState.Failed -> {
                        div(className = "alert alert-error my-4") { +state.message }
                        button(className = "btn w-full") {
                            onClick { onOpenMyReservations() }
                            +currentStrings.claimConfirmOpenMyReservations
                        }
                    }
                }
            }
        }
    }
}
