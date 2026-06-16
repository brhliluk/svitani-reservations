package cz.svitaninymburk.projects.reservations.ui.admin.events

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
import cz.svitaninymburk.projects.reservations.admin.EventsPage
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.check.checkBox
import dev.kilua.form.form
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlin.math.ceil
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

private const val DEFINITIONS_PAGE_SIZE = 20
private const val CHILDREN_PAGE_SIZE = 10

private sealed interface AdminEventsUiState {
    data object Loading : AdminEventsUiState
    data class Success(val data: EventsPage) : AdminEventsUiState
    data class Error(val message: String) : AdminEventsUiState
}

@Composable
fun IComponent.AdminEventsScreen() {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val currentStrings by strings

    var definitionsPage by remember { mutableStateOf(0) }
    val childrenPageByDef = remember { mutableStateMapOf<Uuid, Int>() }
    val scope = rememberCoroutineScope()
    var toastData by remember { mutableStateOf<ToastData?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var deleteDefinitionPending by remember { mutableStateOf<AdminEventListItem?>(null) }
    var deleteItemPending by remember { mutableStateOf<AdminEventListItem?>(null) }
    var refundMoney by remember { mutableStateOf(true) }
    var hideItemPending by remember { mutableStateOf<AdminEventListItem?>(null) }

    val uiState by produceState<AdminEventsUiState>(
        initialValue = AdminEventsUiState.Loading,
        key1 = refreshTrigger,
        key2 = definitionsPage,
    ) {
        adminService.getAllEvents(definitionsPage, DEFINITIONS_PAGE_SIZE)
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

                val definitions = data.items.filter { it.isDefinitionOnly }.sortedBy { it.title }
                val childrenByDef = data.items.filter { !it.isDefinitionOnly }
                    .groupBy { it.definitionId }
                val totalDefinitionPages = maxOf(1, ceil(data.totalDefinitionCount.toDouble() / DEFINITIONS_PAGE_SIZE).toInt())

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
                                        button(className = "btn btn-xs btn-ghost text-primary tooltip tooltip-bottom") {
                                            attribute("data-tip", currentStrings.tooltipEditDefinition)
                                            onClick { router.navigate("/admin/events/definition/${def.id}/edit") }
                                            span(className = "icon-[heroicons--pencil] size-3")
                                        }
                                        button(className = "btn btn-xs btn-ghost text-error tooltip tooltip-bottom") {
                                            attribute("data-tip", currentStrings.tooltipDeleteDefinition)
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
                                    val childPage = childrenPageByDef[def.id] ?: 0
                                    val visibleChildren = children.drop(childPage * CHILDREN_PAGE_SIZE).take(CHILDREN_PAGE_SIZE)
                                    val totalChildPages = maxOf(1, ceil(children.size.toDouble() / CHILDREN_PAGE_SIZE).toInt())

                                    div(className = "overflow-x-auto") {
                                        table(className = "table table-sm w-full") {
                                            tbody {
                                                visibleChildren.forEach { item ->
                                                    tr(className = "hover cursor-pointer") {
                                                        onClick {
                                                            val typePath = if (item.isSeries) "series" else "instance"
                                                            router.navigate("/admin/events/$typePath/${item.id}")
                                                        }
                                                        td(className = "pl-8 font-medium") {
                                                            +item.title
                                                            if (!item.isDefinitionOnly) {
                                                                if (item.isCancelled) {
                                                                    div(className = "badge badge-error badge-sm gap-1 ml-2") {
                                                                        span(className = "icon-[heroicons--x-circle] size-3")
                                                                        +currentStrings.cancelled
                                                                    }
                                                                } else if (item.isPublished) {
                                                                    span(className = "badge badge-primary badge-sm ml-2") { +currentStrings.statusPublished }
                                                                } else {
                                                                    span(className = "badge badge-ghost badge-sm ml-2") { +currentStrings.statusHidden }
                                                                }
                                                            }
                                                        }
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
                                                                button(className = "btn btn-ghost btn-xs btn-circle tooltip tooltip-left") {
                                                                    attribute("data-tip", currentStrings.tooltipEditEvent)
                                                                    span(className = "icon-[heroicons--pencil] size-4 text-base-content/50")
                                                                    onClick {
                                                                        it.stopPropagation()
                                                                        val typePath = if (item.isSeries) "series" else "instance"
                                                                        router.navigate("/admin/events/$typePath/${item.id}/edit")
                                                                    }
                                                                }
                                                                if (!item.isDefinitionOnly) {
                                                                    button(className = "btn btn-ghost btn-xs btn-circle tooltip tooltip-left") {
                                                                        attribute("data-tip", if (item.isPublished) currentStrings.tooltipHide else currentStrings.tooltipPublish)
                                                                        if (item.isPublished) {
                                                                            span(className = "icon-[heroicons--eye-slash] size-4 text-base-content/50")
                                                                        } else {
                                                                            span(className = "icon-[heroicons--eye] size-4 text-base-content/50")
                                                                        }
                                                                        onClick {
                                                                            it.stopPropagation()
                                                                            if (item.isPublished && item.occupiedSpots > 0) {
                                                                                hideItemPending = item
                                                                            } else {
                                                                                scope.launch {
                                                                                    val result = if (item.isSeries)
                                                                                        adminService.setSeriesPublished(item.id, !item.isPublished)
                                                                                    else
                                                                                        adminService.setInstancePublished(item.id, !item.isPublished)
                                                                                    result
                                                                                        .onRight {
                                                                                            val msg = if (item.isPublished) currentStrings.toastHidden else currentStrings.toastPublished
                                                                                            toastData = ToastData(msg, ToastType.Success)
                                                                                            definitionsPage = 0
                                                                                            refreshTrigger++
                                                                                        }
                                                                                        .onLeft { toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error) }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                button(className = "btn btn-ghost btn-xs btn-circle text-error tooltip tooltip-left") {
                                                                    attribute("data-tip", currentStrings.tooltipDeleteEvent)
                                                                    span(className = "icon-[heroicons--trash] size-4")
                                                                    onClick {
                                                                        it.stopPropagation()
                                                                        refundMoney = true
                                                                        deleteItemPending = item
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                if (totalChildPages > 1) {
                                                    tr {
                                                        td {
                                                            attribute("colspan", "6")
                                                            div(className = "flex items-center justify-center gap-3 py-1") {
                                                                button(className = "btn btn-ghost btn-xs") {
                                                                    disabled(childPage == 0)
                                                                    onClick { if (childPage > 0) childrenPageByDef[def.id] = childPage - 1 }
                                                                    +currentStrings.paginationPrevious
                                                                }
                                                                span(className = "text-xs text-base-content/50") {
                                                                    +currentStrings.paginationPageOf(childPage + 1, totalChildPages)
                                                                }
                                                                button(className = "btn btn-ghost btn-xs") {
                                                                    disabled(childPage >= totalChildPages - 1)
                                                                    onClick { if (childPage < totalChildPages - 1) childrenPageByDef[def.id] = childPage + 1 }
                                                                    +currentStrings.paginationNext
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

                        // Definition-level pagination
                        if (data.totalDefinitionCount > DEFINITIONS_PAGE_SIZE) {
                            div(className = "flex items-center justify-center gap-4 mt-2") {
                                button(className = "btn btn-outline btn-sm") {
                                    disabled(definitionsPage == 0)
                                    onClick { if (definitionsPage > 0) definitionsPage-- }
                                    +currentStrings.paginationPrevious
                                }
                                span(className = "text-sm text-base-content/70") {
                                    +currentStrings.paginationPageOf(definitionsPage + 1, totalDefinitionPages)
                                }
                                button(className = "btn btn-outline btn-sm") {
                                    disabled(definitionsPage >= totalDefinitionPages - 1)
                                    onClick { if (definitionsPage < totalDefinitionPages - 1) definitionsPage++ }
                                    +currentStrings.paginationNext
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
                                                    definitionsPage = 0
                                                    refreshTrigger++
                                                }
                                                .onLeft { toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error) }
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

                // Hide confirmation modal (published event with reservations)
                val itemToHide = hideItemPending
                if (itemToHide != null) {
                    div(className = "modal modal-open") {
                        div(className = "modal-box") {
                            h3(className = "font-bold text-lg") { +currentStrings.hideButton }
                            p(className = "py-4") { +currentStrings.hideWithReservationsConfirm }
                            div(className = "modal-action") {
                                button(className = "btn") { onClick { hideItemPending = null }; +currentStrings.modalBack }
                                button(className = "btn btn-warning") {
                                    onClick {
                                        val toHide = itemToHide
                                        hideItemPending = null
                                        scope.launch {
                                            val result = if (toHide.isSeries)
                                                adminService.setSeriesPublished(toHide.id, false)
                                            else
                                                adminService.setInstancePublished(toHide.id, false)
                                            result
                                                .onRight {
                                                    toastData = ToastData(currentStrings.toastHidden, ToastType.Success)
                                                    definitionsPage = 0
                                                    refreshTrigger++
                                                }
                                                .onLeft { toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error) }
                                        }
                                    }
                                    +currentStrings.hideButton
                                }
                            }
                        }
                        form(className = "modal-backdrop") {
                            button { onClick { hideItemPending = null }; +currentStrings.close }
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
                            div(className = "form-control mt-2") {
                                label(className = "label cursor-pointer justify-start gap-3") {
                                    checkBox(value = refundMoney, className = "toggle toggle-error") {
                                        onChange { refundMoney = value }
                                    }
                                    span(className = "label-text") { +currentStrings.refundOnCancelLabel }
                                }
                            }
                            div(className = "modal-action") {
                                button(className = "btn") { onClick { deleteItemPending = null }; +currentStrings.modalBack }
                                button(className = "btn btn-error") {
                                    onClick {
                                        val toDelete = itemToDelete
                                        deleteItemPending = null
                                        scope.launch {
                                            val result = if (toDelete.isSeries)
                                                adminService.deleteEventSeries(toDelete.id, refundMoney)
                                            else
                                                adminService.deleteEventInstance(toDelete.id, refundMoney)
                                            result
                                                .onRight {
                                                    val msg = if (toDelete.isSeries) currentStrings.toastSeriesDeleted else currentStrings.toastEventDeleted
                                                    toastData = ToastData(msg, ToastType.Success)
                                                    definitionsPage = 0
                                                    refreshTrigger++
                                                }
                                                .onLeft { toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error) }
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
