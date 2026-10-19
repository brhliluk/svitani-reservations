package cz.svitaninymburk.projects.reservations.ui.auth

import cz.svitaninymburk.projects.reservations.ui.util.ModalBackdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.p
import dev.kilua.html.span

/**
 * Obal přihlašovacích dialogů: rámec, křížek, titulek a podklad. Byl rozepsaný
 * ve čtyřech kopiích (přihlášení, registrace, zapomenuté heslo, změna hesla).
 */
@Composable
fun IComponent.AuthDialog(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    content: @Composable IComponent.() -> Unit,
) {
    val currentStrings by strings

    div(className = "modal modal-open modal-bottom sm:modal-middle") {
        div(className = "modal-box") {
            button(className = "btn btn-sm btn-circle btn-ghost absolute right-2 top-2") {
                attribute("aria-label", currentStrings.close)
                onClick { onClose() }
                +"✕"
            }

            h3(className = "font-bold text-lg mb-4") { +title }
            subtitle?.let {
                p(className = "text-sm text-base-content/70 mb-4") { +it }
            }

            content()
        }
        ModalBackdrop(onDismiss = onClose)
    }
}

/** Chybový alert pod formulářem, stejný ve všech přihlašovacích dialozích. */
@Composable
fun IComponent.AuthErrorAlert(message: String?) {
    message ?: return
    div(className = "alert alert-error mt-4 text-sm py-2") {
        span(className = "icon-[heroicons--exclamation-circle] size-5")
        span { +message }
    }
}
