package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.*
import web.history.history

@Composable
fun IComponent.EventDetailHeader(
    data: AdminEventDetailData,
    eventId: String,
    isSeries: Boolean,
    onCancelEvent: () -> Unit,
    onDeleteEvent: () -> Unit,
) {
    val router = Router.current
    val currentStrings by strings

    div(className = "flex flex-col gap-2") {
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { history.back() }
            }
            div {
                h1(className = "text-2xl font-bold text-base-content flex items-center gap-2") {
                    if (isSeries) span(className = "icon-[heroicons--academic-cap] text-secondary size-6")
                    else span(className = "icon-[heroicons--calendar] text-primary size-6")

                    +data.title
                }
                p(className = "text-base-content/60 text-sm") { +data.subtitle }
            }
        }
        div(className = "flex flex-wrap items-center gap-2") {
            val navPrefix = if (isSeries) "/admin/events/series" else "/admin/events/instance"
            button(className = "btn btn-outline btn-sm gap-2") {
                span(className = "icon-[heroicons--clipboard-document-check] size-4")
                +currentStrings.attendanceButton
                onClick { router.navigate("$navPrefix/$eventId/attendance") }
            }
            button(className = "btn btn-outline btn-sm gap-2") {
                span(className = "icon-[heroicons--pencil] size-4")
                if (isSeries) +currentStrings.editSeries else +currentStrings.editEvent
                onClick { router.navigate("$navPrefix/$eventId/edit") }
            }
            button(className = "btn btn-outline btn-info btn-sm gap-2") {
                span(className = "icon-[heroicons--eye] size-4")
                +currentStrings.viewAsCustomer
                onClick { router.navigate("$navPrefix/$eventId/preview") }
            }
            if (data.isCancelled) {
                div(className = "badge badge-error gap-2") {
                    span(className = "icon-[heroicons--x-circle] size-4")
                    +currentStrings.cancelled
                }
            } else {
                button(className = "btn btn-outline btn-warning btn-sm gap-2") {
                    span(className = "icon-[heroicons--x-circle] size-4")
                    +currentStrings.cancelEventLabel
                    onClick { onCancelEvent() }
                }
            }
            button(className = "btn btn-outline btn-error btn-sm gap-2") {
                span(className = "icon-[heroicons--trash] size-4")
                if (isSeries) +currentStrings.deleteSeriesLabel else +currentStrings.deleteEventLabel
                onClick { onDeleteEvent() }
            }
        }
    }
}

@Composable
fun IComponent.EventDetailStats(data: AdminEventDetailData) {
    val currentStrings by strings

    div(className = "stats shadow-sm bg-base-100 w-full") {
        div(className = "stat") {
            div(className = "stat-title") { +currentStrings.occupancyStatTitle }
            val isFull = data.occupiedSpots >= data.capacity
            div(className = "stat-value ${if (isFull) "text-error" else "text-primary"}") {
                +"${data.occupiedSpots} / ${data.capacity}"
            }
            div(className = "stat-desc") {
                if (isFull) +currentStrings.capacityFilled else +currentStrings.spotsRemaining(data.capacity - data.occupiedSpots)
            }
        }
        div(className = "stat") {
            div(className = "stat-title") { +currentStrings.revenueStatTitle }
            div(className = "stat-value text-success") { +"${data.totalCollected} ${currentStrings.currency}" }
            div(className = "stat-desc") { +currentStrings.revenueStatDesc }
        }
    }
}
