package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.CHILDREN_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.DEFINITIONS_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.childrenByDefinition
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.definitionRows
import cz.svitaninymburk.projects.reservations.ui.util.pageCount
import cz.svitaninymburk.projects.reservations.ui.util.pageSlice
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.check.checkBox
import dev.kilua.form.form
import dev.kilua.html.*

@Composable
fun IComponent.AdminEventsScreen() {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildAdminEventsModel(scope) }

    LaunchedEffect(Unit) { model.load() }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HLAVIČKA A TLAČÍTKA ---
        div(className = "flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4") {
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.navEvents }
                p(className = "text-base-content/60 mt-1") { +currentStrings.adminEventsSubtitle }
            }

            div(className = "flex items-center gap-4") {
                label(className = "label cursor-pointer gap-2") {
                    span(className = "label-text text-sm") { +currentStrings.showPastLabel }
                    checkBox(value = model.includePast, className = "toggle toggle-sm toggle-primary") {
                        onChange { model.setIncludePast(value) }
                    }
                }
                button(className = "btn btn-primary") {
                    span(className = "icon-[heroicons--plus] size-5")
                    +currentStrings.createNew
                    onClick { router.navigate("/admin/events/new") }
                }
            }
        }

        // --- 2. TABULKA ---
        when (val state = model.uiState) {
            is AdminEventsUiState.Loading -> Loading()
            is AdminEventsUiState.Error -> {
                div(className = "alert alert-error") { +currentStrings.loadingError(state.message) }
            }
            is AdminEventsUiState.Success -> {
                val data = state.data

                val definitions = definitionRows(data)
                val childrenByDef = childrenByDefinition(data)
                val totalDefinitionPages = pageCount(data.totalDefinitionCount, DEFINITIONS_PAGE_SIZE)

                if (definitions.isEmpty()) {
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body") {
                            div(className = "text-center text-base-content/50 py-8") { +currentStrings.emptyTemplates }
                        }
                    }
                } else {
                    div(className = "flex flex-col gap-4") {
                        definitions.forEach { def ->
                            val children = childrenByDef[def.id] ?: emptyList()
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
                                            onClick { model.requestDeleteDefinition(def) }
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
                                    val childPage = model.childrenPageByDef[def.id] ?: 0
                                    val visibleChildren = pageSlice(children, childPage, CHILDREN_PAGE_SIZE)
                                    val totalChildPages = pageCount(children.size.toLong(), CHILDREN_PAGE_SIZE)

                                    div(className = "overflow-x-auto") {
                                        table(className = "table table-sm w-full") {
                                            tbody {
                                                visibleChildren.forEach { item ->
                                                    tr(className = "hover cursor-pointer" + if (item.isPast) " opacity-50" else "") {
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
                                                                if (item.isPast) {
                                                                    span(className = "badge badge-ghost badge-sm ml-2") { +currentStrings.badgePast }
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
                                                                            model.togglePublished(item)
                                                                        }
                                                                    }
                                                                }
                                                                button(className = "btn btn-ghost btn-xs btn-circle text-error tooltip tooltip-left") {
                                                                    attribute("data-tip", currentStrings.tooltipDeleteEvent)
                                                                    span(className = "icon-[heroicons--trash] size-4")
                                                                    onClick {
                                                                        it.stopPropagation()
                                                                        model.requestDeleteItem(item)
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
                                                                    onClick { if (childPage > 0) model.setChildPage(def.id, childPage - 1) }
                                                                    +currentStrings.paginationPrevious
                                                                }
                                                                span(className = "text-xs text-base-content/50") {
                                                                    +currentStrings.paginationPageOf(childPage + 1, totalChildPages)
                                                                }
                                                                button(className = "btn btn-ghost btn-xs") {
                                                                    disabled(childPage >= totalChildPages - 1)
                                                                    onClick { if (childPage < totalChildPages - 1) model.setChildPage(def.id, childPage + 1) }
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
                                    disabled(model.definitionsPage == 0)
                                    onClick { if (model.definitionsPage > 0) model.setDefinitionsPage(model.definitionsPage - 1) }
                                    +currentStrings.paginationPrevious
                                }
                                span(className = "text-sm text-base-content/70") {
                                    +currentStrings.paginationPageOf(model.definitionsPage + 1, totalDefinitionPages)
                                }
                                button(className = "btn btn-outline btn-sm") {
                                    disabled(model.definitionsPage >= totalDefinitionPages - 1)
                                    onClick { if (model.definitionsPage < totalDefinitionPages - 1) model.setDefinitionsPage(model.definitionsPage + 1) }
                                    +currentStrings.paginationNext
                                }
                            }
                        }
                    }
                }

                // Definition delete modal
                val defToDelete = model.deleteDefinitionPending
                if (defToDelete != null) {
                    val children = childrenByDef[defToDelete.id] ?: emptyList()
                    val totalReservations = children.sumOf { it.occupiedSpots }
                    div(className = "modal modal-open") {
                        div(className = "modal-box") {
                            h3(className = "font-bold text-lg text-error") { +currentStrings.confirmDeleteTitle }
                            p(className = "py-4") { +currentStrings.deleteDefinitionImpact(children.size, totalReservations) }
                            div(className = "modal-action") {
                                button(className = "btn") { onClick { model.dismissDeleteDefinition() }; +currentStrings.modalBack }
                                button(className = "btn btn-error") {
                                    onClick { model.confirmDeleteDefinition() }
                                    +currentStrings.deleteTemplate
                                }
                            }
                        }
                        form(className = "modal-backdrop") {
                            button { onClick { model.dismissDeleteDefinition() }; +currentStrings.close }
                        }
                    }
                }

                // Hide confirmation modal (published event with reservations)
                val itemToHide = model.hideItemPending
                if (itemToHide != null) {
                    div(className = "modal modal-open") {
                        div(className = "modal-box") {
                            h3(className = "font-bold text-lg") { +currentStrings.hideButton }
                            p(className = "py-4") { +currentStrings.hideWithReservationsConfirm }
                            div(className = "modal-action") {
                                button(className = "btn") { onClick { model.dismissHide() }; +currentStrings.modalBack }
                                button(className = "btn btn-warning") {
                                    onClick { model.confirmHide() }
                                    +currentStrings.hideButton
                                }
                            }
                        }
                        form(className = "modal-backdrop") {
                            button { onClick { model.dismissHide() }; +currentStrings.close }
                        }
                    }
                }

                // Instance/Series delete modal
                val itemToDelete = model.deleteItemPending
                if (itemToDelete != null) {
                    div(className = "modal modal-open") {
                        div(className = "modal-box") {
                            h3(className = "font-bold text-lg text-error") { +currentStrings.confirmDeleteTitle }
                            p(className = "py-4") { +currentStrings.deleteEventImpact(itemToDelete.occupiedSpots) }
                            div(className = "form-control mt-2") {
                                label(className = "label cursor-pointer justify-start gap-3") {
                                    checkBox(value = model.refundMoney, className = "toggle toggle-error") {
                                        onChange { model.refundMoney = value }
                                    }
                                    span(className = "label-text") { +currentStrings.refundOnCancelLabel }
                                }
                            }
                            div(className = "modal-action") {
                                button(className = "btn") { onClick { model.dismissDeleteItem() }; +currentStrings.modalBack }
                                button(className = "btn btn-error") {
                                    onClick { model.confirmDeleteItem() }
                                    if (itemToDelete.isSeries) +currentStrings.deleteSeriesLabel else +currentStrings.deleteEventLabel
                                }
                            }
                        }
                        form(className = "modal-backdrop") {
                            button { onClick { model.dismissDeleteItem() }; +currentStrings.close }
                        }
                    }
                }
            }
        }

        Toast(message = model.toast?.message, type = model.toast?.type ?: ToastType.Success, onDismiss = { model.dismissToast() })
    }
}
