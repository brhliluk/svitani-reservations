package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import dev.kilua.core.IComponent
import dev.kilua.html.*
import dev.kilua.rpc.getService
import web.history.history

@Composable
fun IComponent.AdminEventCreateChooseScreen(definitionId: String) {
    val router = Router.current
    val eventService = getService<EventServiceInterface>(RpcSerializersModules)
    val currentStrings by strings

    val definitionTitle by produceState<String?>(initialValue = null) {
        eventService.getAllDefinitions()
            .onRight { defs -> value = defs.find { it.id.toString() == definitionId }?.title }
    }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-2xl mx-auto pb-20") {

        // Hlavička
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { history.back() }
            }
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.chooseTypeTitle }
                p(className = "text-base-content/60 mt-1") {
                    +(definitionTitle?.let { currentStrings.chooseTypeSubtitleWith(it) } ?: currentStrings.chooseTypeSubtitle)
                }
            }
        }

        // Volba
        div(className = "grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4") {

            // Jednorázový termín
            div(className = "card bg-base-100 shadow-sm border-2 border-transparent hover:border-primary hover:shadow-md transition-all cursor-pointer") {
                onClick { router.navigate("/admin/events/create/instance/$definitionId") }
                div(className = "card-body items-center text-center gap-4 py-10") {
                    span(className = "icon-[heroicons--calendar] size-16 text-primary")
                    div {
                        h2(className = "card-title justify-center text-xl") { +currentStrings.instanceCardTitle }
                        p(className = "text-base-content/60 text-sm mt-1") { +currentStrings.instanceCardDescription }
                    }
                }
            }

            // Kurz / více lekcí
            div(className = "card bg-base-100 shadow-sm border-2 border-transparent hover:border-secondary hover:shadow-md transition-all cursor-pointer") {
                onClick { router.navigate("/admin/events/create/series/$definitionId") }
                div(className = "card-body items-center text-center gap-4 py-10") {
                    span(className = "icon-[heroicons--academic-cap] size-16 text-secondary")
                    div {
                        h2(className = "card-title justify-center text-xl") { +currentStrings.seriesCardTitle }
                        p(className = "text-base-content/60 text-sm mt-1") { +currentStrings.seriesCardDescription }
                    }
                }
            }
        }
    }
}
