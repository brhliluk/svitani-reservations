package cz.svitaninymburk.projects.reservations.slot.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.SLOT_TAB_PARAM
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.adminSlotServiceLink
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.slotAdminTabOf
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotAdminStrings
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.ui.util.formatAmount
import dev.kilua.core.IComponent
import dev.kilua.form.check.checkBox
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.label
import dev.kilua.html.p
import dev.kilua.html.span
import kotlin.uuid.Uuid

/** Adresa seznamu služeb v administraci. */
const val ADMIN_SLOTS_PATH = "/admin/slots"

internal const val ADMIN_CARD = "card bg-base-100 shadow-sm border border-base-200"

@Composable
internal fun IComponent.LoadFailed(message: String, onRetry: () -> Unit) {
    div(className = "alert alert-error") {
        span(className = "icon-[heroicons--exclamation-circle] size-6")
        span { +message }
        button(className = "btn btn-sm") {
            onClick { onRetry() }
            +slotAdminStrings.retry
        }
    }
}

@Composable
internal fun IComponent.Spinner() {
    div(className = "flex justify-center py-10") {
        span(className = "loading loading-spinner loading-lg text-primary")
    }
}

// --- Seznam ---

@Composable
fun IComponent.AdminSlotServicesScreen() {
    val s = slotAdminStrings
    val currentStrings by strings
    val router = Router.current
    val scope = rememberCoroutineScope()
    val model = remember { buildAdminSlotServicesModel(scope) }

    LaunchedEffect(Unit) { model.load() }

    div(className = "flex flex-col gap-6 animate-fade-in") {
        div(className = "flex flex-col md:flex-row justify-between items-start md:items-center gap-4") {
            div {
                h1(className = "text-3xl font-bold text-base-content") { +s.listTitle }
                p(className = "text-base-content/60 mt-1") { +s.listSubtitle }
            }
            button(className = "btn btn-primary gap-2") {
                onClick { router.navigate("$ADMIN_SLOTS_PATH/new") }
                span(className = "icon-[heroicons--plus] size-5")
                +s.newService
            }
        }

        when (val state = model.state) {
            LoadState.Loading -> Spinner()
            is LoadState.Failed -> LoadFailed(state.message) { model.load() }
            is LoadState.Loaded -> if (state.value.isEmpty()) {
                div(className = "alert bg-base-100 shadow-sm") {
                    span(className = "icon-[heroicons--information-circle] size-6 text-info")
                    span { +s.noServicesYet }
                }
            } else {
                div(className = "flex flex-col gap-3") {
                    state.value.forEach { service ->
                        button(className = "$ADMIN_CARD p-5 text-left hover:shadow-md transition-shadow flex items-center gap-4") {
                            onClick { router.navigate("$ADMIN_SLOTS_PATH/${service.id}") }
                            span(className = "icon-[heroicons--clock] size-7 text-primary shrink-0")
                            div(className = "flex flex-col gap-1 flex-1 min-w-0") {
                                div(className = "flex items-center gap-2 flex-wrap") {
                                    span(className = "font-bold text-lg") { +service.title }
                                    if (service.isPublished) {
                                        span(className = "badge badge-success badge-sm") { +s.published }
                                    } else {
                                        span(className = "badge badge-ghost badge-sm") { +s.hidden }
                                    }
                                }
                                span(className = "text-sm text-base-content/60") {
                                    +s.serviceSummary(
                                        formatAmount(service.pricePerHour, currentStrings),
                                        service.capacity,
                                        service.step.inWholeMinutes,
                                    )
                                }
                            }
                            span(className = "icon-[heroicons--chevron-right] size-5 text-base-content/40 shrink-0")
                        }
                    }
                }
            }
        }
    }
}

// --- Detail / nová služba ---

/**
 * `/admin/slots/{id}`; `new` založí novou službu. `?tab=schedule` otevře detail rovnou
 * na dané záložce (tak se sem přesměrovává po založení).
 *
 * `key(id)`: každá adresa má vlastní stav — modely z `remember` nepřežijí přechod
 * z formuláře nové služby na detail ani mezi dvěma službami.
 */
@Composable
fun IComponent.AdminSlotServiceScreen(id: String) {
    key(id) {
        val router = Router.current
        val serviceId = remember { try { Uuid.parse(id) } catch (_: IllegalArgumentException) { null } }
        when {
            id == "new" -> NewSlotServiceScreen()
            serviceId == null -> LaunchedEffect(Unit) { router.navigate(ADMIN_SLOTS_PATH) }
            else -> {
                val initialTab = remember {
                    slotAdminTabOf(router.currentPath().parameters?.map?.get(SLOT_TAB_PARAM)?.firstOrNull())
                }
                SlotServiceDetail(serviceId, initialTab)
            }
        }
    }
}

@Composable
private fun IComponent.BackToList() {
    val router = Router.current
    button(className = "btn btn-ghost btn-sm self-start gap-2 print:hidden") {
        onClick { router.navigate(ADMIN_SLOTS_PATH) }
        span(className = "icon-[heroicons--arrow-left] size-5")
        +slotAdminStrings.backToList
    }
}

@Composable
private fun IComponent.NewSlotServiceScreen() {
    val router = Router.current
    val scope = rememberCoroutineScope()
    // replace = true: zpět z detailu vede na seznam, ne znovu na formulář, který by založil další službu.
    val model = remember {
        buildNewSlotServiceModel(scope) { id -> router.navigate(adminSlotServiceLink(id, SlotAdminTab.SCHEDULE), replace = true) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in") {
        BackToList()
        h1(className = "text-3xl font-bold text-base-content") { +slotAdminStrings.newService }
        SettingsTab(model)
    }
    Toast(message = model.toast?.message, type = model.toast?.type ?: ToastType.Error, onDismiss = { model.dismissToast() })
}

@Composable
private fun IComponent.SlotServiceDetail(serviceId: Uuid, initialTab: SlotAdminTab) {
    val s = slotAdminStrings
    val currentStrings by strings
    val router = Router.current
    val scope = rememberCoroutineScope()
    val model = remember(serviceId) {
        buildAdminSlotServiceModel(scope, serviceId, initialTab) { router.navigate(ADMIN_SLOTS_PATH) }
    }

    LaunchedEffect(serviceId) { model.load() }

    div(className = "flex flex-col gap-6 animate-fade-in") {
        BackToList()
        when (val state = model.state) {
            LoadState.Loading -> Spinner()
            is LoadState.Failed -> LoadFailed(state.message) { model.load() }
            is LoadState.Loaded -> {
                val service = state.value.service
                div(className = "flex flex-col md:flex-row justify-between items-start md:items-center gap-4") {
                    div(className = "flex items-center gap-3 flex-wrap") {
                        h1(className = "text-3xl font-bold text-base-content") { +service.title }
                        if (service.isPublished) {
                            span(className = "badge badge-success print:hidden") { +s.published }
                        } else {
                            span(className = "badge badge-ghost print:hidden") { +s.hidden }
                        }
                    }
                    div(className = "flex gap-2 print:hidden") {
                        button(className = "btn btn-outline btn-sm gap-2") {
                            disabled(model.isPublishing)
                            onClick { model.togglePublished() }
                            if (service.isPublished) {
                                span(className = "icon-[heroicons--eye-slash] size-4")
                                +s.unpublish
                            } else {
                                span(className = "icon-[heroicons--eye] size-4")
                                +s.publish
                            }
                        }
                        button(className = "btn btn-outline btn-error btn-sm gap-2") {
                            onClick { model.askDelete() }
                            span(className = "icon-[heroicons--trash] size-4")
                            +s.deleteService
                        }
                    }
                }

                div(className = "tabs tabs-box w-fit print:hidden") {
                    attribute("role", "tablist")
                    listOf(
                        SlotAdminTab.DAY to s.tabDay,
                        SlotAdminTab.SCHEDULE to s.tabSchedule,
                        SlotAdminTab.SETTINGS to s.tabSettings,
                    ).forEach { (tab, label) ->
                        button(className = if (model.tab == tab) "tab tab-active" else "tab") {
                            attribute("role", "tab")
                            attribute("aria-selected", (model.tab == tab).toString())
                            onClick { model.tab = tab }
                            +label
                        }
                    }
                }

                when (model.tab) {
                    SlotAdminTab.DAY -> DayTab(model.day, service)
                    SlotAdminTab.SCHEDULE -> ScheduleTab(model.schedule)
                    SlotAdminTab.SETTINGS -> SettingsTab(model.settings)
                }

                if (model.showDelete) {
                    ConfirmModal(
                        title = s.deleteTitle,
                        confirmLabel = s.confirmDelete,
                        dismissLabel = currentStrings.cancel,
                        isLoading = model.isDeleting,
                        onConfirm = { model.confirmDelete() },
                        onDismiss = { model.dismissDelete() },
                    ) {
                        p(className = "py-2") { +s.deleteText(service.title, state.value.upcomingReservationCount) }
                        label(className = "flex items-center gap-2 cursor-pointer") {
                            checkBox(value = model.refundOnDelete, className = "checkbox checkbox-sm") {
                                onChange { model.refundOnDelete = value }
                            }
                            span(className = "label-text") { +s.refundCredit }
                        }
                    }
                }
            }
        }
    }

    listOf(model.toast, model.day.toast, model.schedule.toast, model.settings.toast).firstOrNull { it != null }?.let { toast ->
        Toast(message = toast.message, type = toast.type) {
            model.dismissToast(); model.day.dismissToast(); model.schedule.dismissToast(); model.settings.dismissToast()
        }
    }
}
