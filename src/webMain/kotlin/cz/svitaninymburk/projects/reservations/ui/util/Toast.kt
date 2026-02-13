package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.span
import kotlinx.coroutines.delay

// 1. Definice typů
enum class ToastType(val cssClass: String, val iconClass: String) {
    Success("alert-success", "icon-[heroicons--check-circle]"),
    Error("alert-error", "icon-[heroicons--x-circle]"),
    Warning("alert-warning", "icon-[heroicons--exclamation-triangle]"),
    Info("alert-info", "icon-[heroicons--information-circle]")
}

data class ToastData(val message: String, val type: ToastType = ToastType.Success)

@Composable
fun IComponent.Toast(
    message: String?,
    type: ToastType = ToastType.Success,
    durationMs: Long = 4000,
    onDismiss: () -> Unit
) {
    if (message != null) {
        // DaisyUI Toast
        div(className = "toast toast-top toast-center z-[200]") {
            div(className = "alert ${type.cssClass} shadow-lg") {
                span(className = "${type.iconClass} size-6")
                span { +message }
            }
        }

        LaunchedEffect(message, type) { // Restartuje časovač při změně zprávy
            delay(durationMs)
            onDismiss()
        }
    }
}