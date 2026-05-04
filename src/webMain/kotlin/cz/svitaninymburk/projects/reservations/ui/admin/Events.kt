package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.form
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    var toastData by remember { mutableStateOf<ToastData?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var deleteDefinitionPending by remember { mutableStateOf<AdminEventListItem?>(null) }
    var deleteItemPending by remember { mutableStateOf<AdminEventListItem?>(null) }

    val uiState by produceState<AdminEventsUiState>(initialValue = AdminEventsUiState.Loading, key1 = refreshTrigger) {
        adminService.getAllEvents()
            .onRight { value = AdminEventsUiState.Success(it) }
            .onLeft { value = AdminEventsUiState.Error(it.localizedMessage(currentStrings)) }
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
                    onClick { router.navigate("/admin/events/new") }
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
                            val hasInstances = children.any { !it.isSeries }
                            val hasSeries = children.any { it.isSeries }

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
                                        if (!hasSeries) {
                                            button(className = "btn btn-xs btn-outline btn-primary") {
                                                onClick { router.navigate("/admin/events/create/instance/${def.id}") }
                                                span(className = "icon-[heroicons--plus] size-3")
                                                +currentStrings.addDate
                                            }
                                        }
                                        if (!hasInstances) {
                                            button(className = "btn btn-xs btn-outline btn-secondary") {
                                                onClick { router.navigate("/admin/events/create/series/${def.id}") }
                                                span(className = "icon-[heroicons--plus] size-3")
                                                +currentStrings.adminCourse
                                            }
                                        }
                                        button(className = "btn btn-xs btn-ghost text-primary") {
                                            onClick { router.navigate("/admin/events/definition/${def.id}/edit") }
                                            span(className = "icon-[heroicons--pencil] size-3")
                                        }
                                        button(className = "btn btn-xs btn-ghost text-error") {
                                            onClick { deleteDefinitionPending = def }
                                            span(className = "icon-[heroicons--trash] size-3")
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
                                                            div(className = "flex justify-end gap-1") {
                                                                button(className = "btn btn-ghost btn-xs btn-circle") {
                                                                    span(className = "icon-[heroicons--pencil] size-4 text-base-content/50")
                                                                    onClick {
                                                                        it.stopPropagation()
                                                                        val typePath = if (item.isSeries) "series" else "instance"
                                                                        router.navigate("/admin/events/$typePath/${item.id}/edit")
                                                                    }
                                                                }
                                                                button(className = "btn btn-ghost btn-xs btn-circle text-error") {
                                                                    span(className = "icon-[heroicons--trash] size-4")
                                                                    onClick {
                                                                        it.stopPropagation()
                                                                        deleteItemPending = item
                                                                    }
                                                                }
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

                // Definition delete modal
                val defToDelete = deleteDefinitionPending
                if (defToDelete != null) {
                    val children = childrenByDef[defToDelete.id] ?: emptyList()
                    val totalReservations = children.sumOf { it.occupiedSpots }
                    div(className = "modal modal-open") {
                        div(className = "modal-box") {
                            h3(className = "font-bold text-lg text-error") { +currentStrings.confirmDeleteTitle }
                            p(className = "py-4") { +currentStrings.deleteDefinitionImpact(children.size, totalReservations) }
                            div(className = "modal-action") {
                                button(className = "btn") { onClick { deleteDefinitionPending = null }; +currentStrings.modalBack }
                                button(className = "btn btn-error") {
                                    onClick {
                                        deleteDefinitionPending = null
                                        scope.launch {
                                            adminService.deleteEventDefinition(defToDelete.id)
                                                .onRight {
                                                    toastData = ToastData(currentStrings.toastDefinitionDeleted, ToastType.Success)
                                                    refreshTrigger++
                                                }
                                                .onLeft { toastData = ToastData(currentStrings.errorToast(it.toString()), ToastType.Error) }
                                        }
                                    }
                                    +currentStrings.deleteTemplate
                                }
                            }
                        }
                        form(className = "modal-backdrop") {
                            button { onClick { deleteDefinitionPending = null }; +currentStrings.close }
                        }
                    }
                }

                // Instance/Series delete modal
                val itemToDelete = deleteItemPending
                if (itemToDelete != null) {
                    div(className = "modal modal-open") {
                        div(className = "modal-box") {
                            h3(className = "font-bold text-lg text-error") { +currentStrings.confirmDeleteTitle }
                            p(className = "py-4") { +currentStrings.deleteEventImpact(itemToDelete.occupiedSpots) }
                            div(className = "modal-action") {
                                button(className = "btn") { onClick { deleteItemPending = null }; +currentStrings.modalBack }
                                button(className = "btn btn-error") {
                                    onClick {
                                        val toDelete = itemToDelete
                                        deleteItemPending = null
                                        scope.launch {
                                            val result = if (toDelete.isSeries)
                                                adminService.deleteEventSeries(toDelete.id)
                                            else
                                                adminService.deleteEventInstance(toDelete.id)
                                            result
                                                .onRight {
                                                    val msg = if (toDelete.isSeries) currentStrings.toastSeriesDeleted else currentStrings.toastEventDeleted
                                                    toastData = ToastData(msg, ToastType.Success)
                                                    refreshTrigger++
                                                }
                                                .onLeft { toastData = ToastData(currentStrings.errorToast(it.toString()), ToastType.Error) }
                                        }
                                    }
                                    if (itemToDelete.isSeries) +currentStrings.deleteSeriesLabel else +currentStrings.deleteEventLabel
                                }
                            }
                        }
                        form(className = "modal-backdrop") {
                            button { onClick { deleteItemPending = null }; +currentStrings.close }
                        }
                    }
                }
            }
        }

        Toast(message = toastData?.message, type = toastData?.type ?: ToastType.Success, onDismiss = { toastData = null })
    }
}
