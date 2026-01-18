package cz.svitaninymburk.projects.reservations.debug

import androidx.compose.runtime.Composable
import cz.svitaninymburk.projects.reservations.ui.Event
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.h2


@Composable
fun IComponent.ComponentGallery() {
    div(className = "min-h-screen bg-gray-100 p-8") {
        h1(className = "text-2xl font-bold mb-6") { +"Component Gallery" }

        // Sekce pro EventCard
        div(className = "mb-10") {
            h2(className = "text-xl font-semibold mb-4 text-gray-700") { +"EventCard Variants" }

            div(className = "grid grid-cols-1 md:grid-cols-2 gap-6") {
                randomEventList.forEach {
                    Event(it) { }
                }
            }
        }

        div(className = "mb-10") {
            h2(className = "text-xl font-semibold mb-4 text-gray-700") { +"Buttons" }
            div(className = "flex gap-4") {
                button(className = "btn-primary") { +"Primary" }
                button(className = "btn-secondary") { +"Secondary" }
            }
        }
    }
}