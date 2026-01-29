package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.Composable
import dev.kilua.core.IComponent
import dev.kilua.html.div

@Composable
fun IComponent.Loading(
) {
    div(className = "min-h-screen flex items-center justify-center bg-base-200") {
        div(className = "loading loading-spinner loading-lg text-primary")
    }
}