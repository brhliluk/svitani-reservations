package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.p
import dev.kilua.html.span

@Composable
fun IComponent.DefinitionCard(definition: EventDefinition, onClick: () -> Unit) {
    div(className = "card card-bordered bg-base-100 shadow-sm hover:shadow-md hover:border-primary transition-all cursor-pointer group") {
        onClick { onClick() }

        div(className = "card-body") {
            div(className = "w-12 h-12 rounded-xl bg-primary/10 text-primary flex items-center justify-center mb-2 group-hover:bg-primary group-hover:text-white transition-colors") {
                span(className = "icon-[heroicons--sparkles] size-7")
            }

            h3(className = "card-title text-lg") { +definition.title }

            p(className = "text-base-content/70 text-sm line-clamp-3") {
                +definition.description
            }

            div(className = "card-actions justify-between items-center mt-4") {
                div(className = "text-xs font-bold text-base-content/50 uppercase tracking-wide") {
                    // Např. "Od 200 Kč" nebo délka
                    +"${definition.defaultDuration}"
                }

                // Fake button (jen vizuální indikace, že se dá kliknout)
                button(className = "btn btn-sm btn-ghost gap-1 group-hover:text-primary") {
                    +"Termíny"
                    span(className = "icon-[heroicons--arrow-right] size-4")
                }
            }
        }
    }
}