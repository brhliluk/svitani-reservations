package cz.svitaninymburk.projects.reservations.ui.admin.schedule

import cz.svitaninymburk.projects.reservations.util.timeRangeLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.admin.AdminScheduleItem
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.util.pageCount
import cz.svitaninymburk.projects.reservations.ui.admin.schedule.usecase.SCHEDULE_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.ui.util.Pagination
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.form.check.checkBox
import dev.kilua.html.*

@Composable
fun IComponent.AdminScheduleScreen() {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildAdminScheduleModel(scope) }

    LaunchedEffect(Unit) { model.load() }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HLAVIČKA A FILTR ---
        div(className = "flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4") {
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.navSchedule }
                p(className = "text-base-content/60 mt-1") { +currentStrings.adminScheduleSubtitle }
            }

            label(className = "label cursor-pointer gap-2") {
                span(className = "label-text text-sm") { +currentStrings.showPastLabel }
                checkBox(value = model.includePast, className = "toggle toggle-sm toggle-primary") {
                    onChange { model.setIncludePast(value) }
                }
            }
        }

        // --- 2. TABULKA ---
        when (val state = model.uiState) {
            is AdminScheduleUiState.Loading -> Loading()
            is AdminScheduleUiState.Error -> {
                div(className = "alert alert-error") { +currentStrings.loadingError(state.message) }
            }
            is AdminScheduleUiState.Success -> {
                val data = state.data
                val totalPages = pageCount(data.totalCount, SCHEDULE_PAGE_SIZE)

                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body p-0") {
                        if (data.items.isEmpty()) {
                            div(className = "text-center text-base-content/50 py-12") { +currentStrings.scheduleEmpty }
                        } else {
                            div(className = "overflow-x-auto") {
                                table(className = "table table-zebra w-full") {
                                    thead {
                                        tr {
                                            th { +currentStrings.tableHeaderDate }
                                            th { +currentStrings.tableHeaderTime }
                                            th { +currentStrings.tableHeaderEvent }
                                            th { +currentStrings.tableHeaderType }
                                            th { +currentStrings.occupancyStatTitle }
                                        }
                                    }
                                    tbody {
                                        data.items.forEach { item ->
                                            ScheduleRow(item) {
                                                router.navigate("/admin/events/instance/${item.id}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (totalPages > 1) {
                    Pagination(
                        page = model.page,
                        totalPages = totalPages,
                        onPageChange = { model.setPage(it) },
                        className = "flex items-center justify-center gap-4",
                    )
                }
            }
        }

        Toast(message = model.toast?.message, type = model.toast?.type ?: ToastType.Success, onDismiss = { model.dismissToast() })
    }
}

@Composable
private fun IComponent.ScheduleRow(item: AdminScheduleItem, onClick: () -> Unit) {
    val currentStrings by strings
    val isFull = item.occupiedSpots >= item.capacity

    tr(className = "hover cursor-pointer" + if (item.isPast) " opacity-50" else "") {
        onClick { onClick() }
        td(className = "font-medium whitespace-nowrap") { +item.startDateTime.date.humanReadable }
        td(className = "text-sm text-base-content/70 whitespace-nowrap") {
            +timeRangeLabel(item.startDateTime.time, item.endDateTime.time)
        }
        td(className = "font-medium") {
            +item.title
            if (item.isPast) {
                span(className = "badge badge-ghost badge-sm ml-2") { +currentStrings.badgePast }
            }
        }
        td {
            if (item.seriesId != null) {
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
        td {
            div(className = "flex items-center gap-2") {
                span(className = if (isFull) "text-error font-bold" else "") {
                    +"${item.occupiedSpots} / ${item.capacity}"
                }
                if (isFull) {
                    div(className = "badge badge-error badge-xs") { +currentStrings.capacityFull }
                }
            }
        }
    }
}
