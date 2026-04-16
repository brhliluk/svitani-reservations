package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.p
import dev.kilua.html.span

@Composable
fun IComponent.DefinitionCard(definition: EventDefinition, onClick: () -> Unit) {
    val currentStrings by strings
    val totalMinutes = definition.defaultDuration.inWholeMinutes
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val durationText = when {
        h > 0L && m > 0L -> "$h ${currentStrings.hours} $m ${currentStrings.minutes}"
        h > 0L -> "$h ${currentStrings.hours}"
        else -> "$m ${currentStrings.minutes}"
    }

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
                    +durationText
                }

                button(className = "btn btn-sm btn-ghost gap-1 group-hover:text-primary") {
                    +currentStrings.showDates
                    span(className = "icon-[heroicons--arrow-right] size-4")
                }
            }
        }
    }
}