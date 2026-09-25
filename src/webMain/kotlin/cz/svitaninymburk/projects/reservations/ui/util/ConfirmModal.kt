package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.span

@Composable
fun IComponent.ConfirmModal(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    titleClassName: String? = null,
    confirmClassName: String = "btn-error",
    content: @Composable IComponent.() -> Unit = {},
) {
    val currentStrings by strings

    div(className = "modal modal-open") {
        div(className = "modal-box") {
            h3(className = listOfNotNull("font-bold text-lg", titleClassName).joinToString(" ")) { +title }
            content()
            div(className = "modal-action") {
                button(className = "btn") {
                    disabled(isLoading)
                    onClick { onDismiss() }
                    +dismissLabel
                }
                button(className = "btn $confirmClassName") {
                    disabled(isLoading)
                    if (isLoading) span(className = "loading loading-spinner loading-sm")
                    onClick { onConfirm() }
                    +confirmLabel
                }
            }
        }
        ModalBackdrop(enabled = !isLoading, onDismiss = onDismiss)
    }
}

/**
 * Klik vedle modalu ho zavře. Obyčejný div, ne form jako v ukázkách daisyUI —
 * s Kilua form se klik k handleru nedostal a modal zůstával otevřený.
 * [enabled] = false zavírání zamkne (typicky během odesílání).
 */
@Composable
fun IComponent.ModalBackdrop(enabled: Boolean = true, onDismiss: () -> Unit) {
    val currentStrings by strings
    div(className = "modal-backdrop") {
        onClick { if (enabled) onDismiss() }
        button {
            attribute("aria-label", currentStrings.close)
            disabled(!enabled)
        }
    }
}
