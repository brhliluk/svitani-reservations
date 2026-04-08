package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import dev.kilua.core.IComponent
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlin.uuid.Uuid

private sealed interface AdminEventsUiState {
    data object Loading : AdminEventsUiState
    data class Success(val data: List<AdminEventListItem>) : AdminEventsUiState
    data class Error(val message: String) : AdminEventsUiState
}

@Composable
fun IComponent.AdminEventsScreen() {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val currentStrings by strings

    val expandedGroups = remember { mutableStateMapOf<Uuid, Boolean>() }

    val uiState by produceState<AdminEventsUiState>(initialValue = AdminEventsUiState.Loading) {
        adminService.getAllEvents()
            .onRight { value = AdminEventsUiState.Success(it) }
            .onLeft { value = AdminEventsUiState.Error(it.localizedMessage) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HLAVIČKA A TLAČÍTKA ---
        div(className = "flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4") {
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.navEvents }
                p(className = "text-base-content/60 mt-1") { +currentStrings.adminEventsSubtitle }
            }

            // Magické tlačítko, které nás později hodí na formulář
            button(className = "btn btn-primary") {
                span(className = "icon-[heroicons--plus] size-5")
                +currentStrings.createNew
                 onClick { router.navigate("/admin/events/create/definition") } // Přidáme později
            }
        }

        // --- 2. TABULKA ---
        when (val state = uiState) {
            is AdminEventsUiState.Loading -> Loading()
            is AdminEventsUiState.Error -> {
                div(className = "alert alert-error") { +currentStrings.loadingError(state.message) }
            }
            is AdminEventsUiState.Success -> {
                val data = state.data

                val definitions = data.filter { it.isDefinitionOnly }.sortedBy { it.title }
                val childrenByDef = data.filter { !it.isDefinitionOnly }
                    .groupBy { it.definitionId }

                if (definitions.isEmpty()) {
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body") {
                            div(className = "text-center text-base-content/50 py-8") { +currentStrings.emptyTemplates }
                        }
                    }
                } else {
                    div(className = "flex flex-col gap-4") {
                        definitions.forEach { def ->
                            val children = childrenByDef[def.id]?.sortedBy { it.dateInfo } ?: emptyList()

                            div(className = "card bg-base-100 shadow-sm") {
                                // Definition header
                                div(className = "bg-base-200 px-4 py-3 flex items-center justify-between gap-4 rounded-t-2xl") {
                                    div(className = "flex items-center gap-3 min-w-0") {
                                        span(className = "icon-[heroicons--document-text] size-5 text-base-content/50 shrink-0")
                                        span(className = "font-bold text-base-content truncate") { +def.title }
                                        if (children.isEmpty()) {
                                            div(className = "badge badge-ghost badge-sm shrink-0") { +currentStrings.noDates }
                                        } else {
                                            div(className = "badge badge-neutral badge-sm shrink-0") { +currentStrings.datesCount(children.size) }
                                        }
                                    }
                                    div(className = "flex items-center gap-2 shrink-0") {
                                        button(className = "btn btn-xs btn-outline btn-primary") {
                                            onClick { router.navigate("/admin/events/create/instance/${def.id}") }
                                            span(className = "icon-[heroicons--plus] size-3")
                                            +currentStrings.addDate
                                        }
                                        button(className = "btn btn-xs btn-outline btn-secondary") {
                                            onClick { router.navigate("/admin/events/create/series/${def.id}") }
                                            span(className = "icon-[heroicons--plus] size-3")
                                            +currentStrings.adminCourse
                                        }
                                    }
                                }

                                // Children table
                                if (children.isEmpty()) {
                                    div(className = "px-4 py-6 text-center text-sm text-base-content/40 italic") {
                                        +currentStrings.noInstancesMessage
                                    }
                                } else {
                                    val isExpanded = expandedGroups[def.id] ?: false
                                    val visibleChildren = if (isExpanded) children else children.take(3)
                                    val hiddenCount = children.size - 3

                                    div(className = "overflow-x-auto") {
                                        table(className = "table table-sm w-full") {
                                            tbody {
                                                visibleChildren.forEach { item ->
                                                    tr(className = "hover cursor-pointer") {
                                                        onClick {
                                                            val typePath = if (item.isSeries) "series" else "instance"
                                                            router.navigate("/admin/events/$typePath/${item.id}")
                                                        }
                                                        td(className = "pl-8 font-medium") { +item.title }
                                                        td {
                                                            if (item.isSeries) {
                                                                div(className = "badge badge-secondary badge-outline badge-sm gap-1") {
                                                                    span(className = "icon-[heroicons--academic-cap] size-3")
                                                                    +currentStrings.adminCourse
                                                                }
                                                            } else {
                                                                div(className = "badge badge-primary badge-outline badge-sm gap-1") {
                                                                    span(className = "icon-[heroicons--calendar] size-3")
                                                                    +currentStrings.badgeOneTime
                                                                }
                                                            }
                                                        }
                                                        td(className = "text-sm text-base-content/70") { +item.dateInfo }
                                                        td {
                                                            val isFull = item.occupiedSpots >= item.capacity
                                                            div(className = "flex items-center gap-2") {
                                                                span(className = if (isFull) "text-error font-bold" else "") {
                                                                    +"${item.occupiedSpots} / ${item.capacity}"
                                                                }
                                                                if (isFull) {
                                                                    div(className = "badge badge-error badge-xs") { +currentStrings.capacityFull }
                                                                }
                                                            }
                                                        }
                                                        td(className = "font-medium text-base-content/80") { +item.priceString }
                                                        td(className = "text-right") {
                                                            button(className = "btn btn-ghost btn-xs btn-circle") {
                                                                span(className = "icon-[heroicons--chevron-right] size-5 text-base-content/50")
                                                            }
                                                        }
                                                    }
                                                }

                                                if (hiddenCount > 0 || isExpanded) {
                                                    tr {
                                                        td {
                                                            attribute("colspan", "6")
                                                            div(className = "flex justify-center py-1") {
                                                                button(className = "btn btn-ghost btn-xs gap-1 text-base-content/50") {
                                                                    onClick { expandedGroups[def.id] = !isExpanded }
                                                                    if (isExpanded) {
                                                                        span(className = "icon-[heroicons--chevron-up] size-3")
                                                                        +currentStrings.showLess
                                                                    } else {
                                                                        span(className = "icon-[heroicons--chevron-down] size-3")
                                                                        +currentStrings.showMore(hiddenCount)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}