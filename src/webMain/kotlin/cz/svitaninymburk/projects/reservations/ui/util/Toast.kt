package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.span
import kotlinx.coroutines.delay

@Composable
fun IComponent.Toast(
    message: String?,
    onDismiss: () -> Unit
) {
    if (message != null) {
        div(className = "toast toast-top toast-center z-[200]") {
            div(className = "alert alert-success shadow-lg") {
                span(className = "icon-[heroicons--check-circle] size-6")
                span { +message }
            }
        }

        LaunchedEffect(message) {
            delay(4000)
            onDismiss()
        }
    }
}