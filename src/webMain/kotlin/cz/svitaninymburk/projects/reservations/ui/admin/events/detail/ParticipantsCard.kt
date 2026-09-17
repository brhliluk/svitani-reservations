package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.admin.AdminParticipantRow
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.isFreePrice
import cz.svitaninymburk.projects.reservations.ui.util.ReservationStatusBadge
import cz.svitaninymburk.projects.reservations.ui.util.canBeMarkedAsPaid
import cz.svitaninymburk.projects.reservations.ui.util.reservationStatusBadge
import cz.svitaninymburk.projects.reservations.ui.util.totalPriceLabel
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.ReservationExpandedDetails
import cz.svitaninymburk.projects.reservations.util.PhoneNumber
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.html.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

@Composable
fun IComponent.ParticipantsCard(
    data: AdminEventDetailData,
    isLoadingReservationTarget: Boolean,
    isWaitlistSignup: Boolean,
    onAddReservation: (asWaitlist: Boolean) -> Unit,
    onConfirmPayment: (AdminParticipantRow) -> Unit,
    onCancelReservation: (AdminParticipantRow) -> Unit,
) {
    val currentStrings by strings
    var expandedIds by remember { mutableStateOf<Set<Uuid>>(emptySet()) }
    val allExpanded = data.participants.isNotEmpty() &&
        data.participants.all { it.reservationId in expandedIds }

    div(className = "card bg-base-100 shadow-sm") {
        div(className = "card-body p-0") {
            div(className = "px-4 pt-4 pb-2 flex flex-wrap items-center justify-between gap-2 border-b border-base-200") {
                div(className = "flex items-center gap-2") {
                    span(className = "icon-[heroicons--users] size-5 text-primary")
                    h2(className = "font-bold text-lg") { +currentStrings.tableHeaderParticipant }
                }
                div(className = "flex items-center gap-2") {
                    if (data.participants.isNotEmpty()) {
                        button(className = "btn btn-ghost btn-sm gap-2") {
                            onClick {
                                expandedIds = if (allExpanded) emptySet()
                                else data.participants.map { it.reservationId }.toSet()
                            }
                            span(className = "size-4 " + if (allExpanded) "icon-[heroicons--chevron-up]" else "icon-[heroicons--chevron-down]")
                            +if (allExpanded) currentStrings.collapseAllDetails else currentStrings.expandAllDetails
                        }
                    }
                    button(className = "btn btn-primary btn-sm gap-2") {
                        disabled(isLoadingReservationTarget)
                        onClick { onAddReservation(false) }
                        if (isLoadingReservationTarget && !isWaitlistSignup) {
                            span(className = "loading loading-spinner loading-xs")
                        } else {
                            span(className = "icon-[heroicons--plus] size-4")
                        }
                        +currentStrings.addReservation
                    }
                    if (data.waitlistCapacity > 0) {
                        button(className = "btn btn-secondary btn-sm gap-2") {
                            disabled(isLoadingReservationTarget)
                            onClick { onAddReservation(true) }
                            if (isLoadingReservationTarget && isWaitlistSignup) {
                                span(className = "loading loading-spinner loading-xs")
                            } else {
                                span(className = "icon-[heroicons--plus] size-4")
                            }
                            +currentStrings.addSubstitute
                        }
                    }
                }
            }
            div(className = "overflow-x-auto") {
                table(className = "table table-zebra w-full") {
                    thead {
                        tr {
                            th(className = "w-10") { }
                            th { +currentStrings.tableHeaderParticipant }
                            th { +currentStrings.tableHeaderSeats }
                            th { +currentStrings.priceLabel }
                            th { +currentStrings.tableHeaderPaymentStatus }
                            th(className = "text-right") { +currentStrings.tableHeaderActions }
                        }
                    }
                    tbody {
                        if (data.participants.isEmpty()) {
                            tr {
                                td {
                                    attribute("colspan", "6")
                                    div(className = "text-center text-base-content/50 py-4 italic") {
                                        +currentStrings.noParticipants
                                    }
                                }
                            }
                        } else {
                            data.participants.forEach { participant ->
                                ParticipantRow(
                                    participant = participant,
                                    seriesId = data.seriesId,
                                    customFields = data.customFields,
                                    isExpanded = participant.reservationId in expandedIds,
                                    onToggleExpanded = {
                                        expandedIds = if (participant.reservationId in expandedIds) {
                                            expandedIds - participant.reservationId
                                        } else {
                                            expandedIds + participant.reservationId
                                        }
                                    },
                                    onConfirmPayment = { onConfirmPayment(participant) },
                                    onCancelReservation = { onCancelReservation(participant) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IComponent.ParticipantRow(
    participant: AdminParticipantRow,
    seriesId: Uuid?,
    customFields: List<CustomFieldDefinition>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onConfirmPayment: () -> Unit,
    onCancelReservation: () -> Unit,
) {
    val currentStrings by strings
    val router = Router.current

    // Určení vzhledu řádku podle stavu a typu platby
    val badge = reservationStatusBadge(participant.status, participant.paymentType, participant.totalPrice)
    val isPaid = badge == ReservationStatusBadge.PAID || badge == ReservationStatusBadge.FREE
    val isCash = badge == ReservationStatusBadge.ON_SITE

    tr(className = if (!isPaid && isCash) "bg-info/5" else "") {
        td {
            button(className = "btn btn-ghost btn-xs tooltip tooltip-right") {
                attribute("data-tip", if (isExpanded) currentStrings.hideDetails else currentStrings.showDetails)
                onClick { onToggleExpanded() }
                span(className = "size-5 " + if (isExpanded) "icon-[heroicons--chevron-down]" else "icon-[heroicons--chevron-right]")
            }
        }
        td {
            div(className = "flex items-center gap-2") {
                div(className = "font-bold") { +participant.contactName }
                if (participant.fromSeries) {
                    div(className = "badge badge-secondary badge-outline badge-sm gap-1 whitespace-nowrap") {
                        span(className = "icon-[heroicons--academic-cap] size-3")
                        +currentStrings.fromSeriesBadge
                    }
                }
            }
            div(className = "text-xs text-base-content/50") {
                +"${participant.contactEmail} • ${participant.contactPhone?.let { PhoneNumber.format(it) } ?: ""}"
            }
        }
        td { +"${participant.seatCount}" }
        td(className = if (!isPaid && isCash) "font-bold text-info" else "") {
            +totalPriceLabel(participant.totalPrice, currentStrings)
        }
        td {
            div(className = "flex flex-col gap-1 items-start") {
                when (badge) {
                    ReservationStatusBadge.CANCELLED -> div(className = "badge badge-error gap-1") {
                        span(className = "icon-[heroicons--x-mark] size-3")
                        +currentStrings.cancelled
                    }
                    ReservationStatusBadge.WAITLISTED -> div(className = "badge badge-secondary badge-outline gap-1 whitespace-nowrap") {
                        span(className = "icon-[heroicons--queue-list] size-3")
                        +currentStrings.waitlistedStatus
                    }
                    ReservationStatusBadge.FREE -> div(className = "badge badge-success badge-outline gap-1") {
                        span(className = "icon-[heroicons--gift] size-3")
                        +currentStrings.free
                    }
                    ReservationStatusBadge.PAID -> div(className = "badge badge-success gap-1") {
                        span(className = "icon-[heroicons--check] size-3")
                        +currentStrings.paid
                    }
                    ReservationStatusBadge.ON_SITE -> div(className = "badge badge-info badge-outline gap-1") {
                        span(className = "icon-[heroicons--banknotes] size-3")
                        +currentStrings.statusOnSiteBadge
                    }
                    ReservationStatusBadge.WAITING -> div(className = "badge badge-warning gap-1") {
                        span(className = "icon-[heroicons--clock] size-3")
                        +currentStrings.statusWaiting
                    }
                }

                if (!isFreePrice(participant.totalPrice)) {
                    span(className = "text-xs text-base-content/60 font-medium") {
                        if (isCash) +currentStrings.paymentMethodCash else +currentStrings.bankTransfer
                    }
                }
            }
        }
        td(className = "text-right") {
            div(className = "flex justify-end items-center gap-1") {
                // Přihláška na kurz se platí i ruší jako celek, ne po lekcích —
                // odsud vede jen proklik na detail kurzu, kde ty akce dávají smysl.
                if (participant.fromSeries) {
                    if (seriesId != null) {
                        button(className = "btn btn-ghost btn-xs tooltip tooltip-left") {
                            attribute("data-tip", currentStrings.tooltipOpenSeries)
                            onClick { router.navigate("/admin/events/series/$seriesId") }
                            span(className = "icon-[heroicons--arrow-top-right-on-square] size-5")
                        }
                    }
                } else {
                    if (canBeMarkedAsPaid(badge)) {
                        button(className = "btn btn-xs inline-flex items-center gap-1 tooltip tooltip-left ${if (isCash) "btn-outline btn-info" else "btn-ghost text-success"}") {
                            attribute("data-tip", if (isCash) currentStrings.tooltipAcceptCash else currentStrings.tooltipMarkPaid)
                            onClick { onConfirmPayment() }
                            span(className = "icon-[heroicons--check-circle] size-5 flex-none")
                            if (isCash) +currentStrings.buttonCollect
                        }
                    }

                    button(className = "btn btn-ghost btn-xs text-error tooltip tooltip-left") {
                        attribute("data-tip", currentStrings.tooltipCancelReservation)
                        onClick { onCancelReservation() }
                        span(className = "icon-[heroicons--trash] size-5")
                    }
                }
            }
        }
    }

    if (isExpanded) {
        tr(className = "bg-base-200/40") {
            td {
                attribute("colspan", "6")
                div(className = "p-4") {
                    ReservationExpandedDetails(
                        phone = participant.contactPhone,
                        createdAtText = participant.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).humanReadable,
                        customFields = customFields,
                        customValues = participant.customValues,
                    )
                }
            }
        }
    }
}

@Composable
fun IComponent.WaitlistCard(
    data: AdminEventDetailData,
    onCancelReservation: (AdminParticipantRow) -> Unit,
) {
    val currentStrings by strings

    div(className = "card bg-base-100 shadow-sm mt-4") {
        div(className = "card-body p-0") {
            div(className = "px-4 pt-4 pb-2 flex items-center gap-2") {
                span(className = "icon-[heroicons--users] size-5 text-secondary")
                h2(className = "font-bold text-lg") { +currentStrings.substitutesSectionTitle }
                span(className = "text-sm text-base-content/60") {
                    +"(${data.waitlist.size} / ${data.waitlistCapacity})"
                }
            }
            if (data.waitlist.isEmpty()) {
                div(className = "px-4 pb-4") {
                    p(className = "text-base-content/50 italic text-sm") { +currentStrings.noParticipants }
                }
            } else {
                div(className = "overflow-x-auto") {
                    table(className = "table table-sm w-full") {
                        thead {
                            tr {
                                th { +currentStrings.tableHeaderParticipant }
                                th { +currentStrings.tableHeaderSeats }
                                th(className = "text-right") { +currentStrings.tableHeaderActions }
                            }
                        }
                        tbody {
                            data.waitlist.forEach { participant ->
                                tr {
                                    td {
                                        div(className = "font-medium") { +participant.contactName }
                                        div(className = "text-xs text-base-content/60") { +participant.contactEmail }
                                    }
                                    td { +"${participant.seatCount}" }
                                    td(className = "text-right") {
                                        button(className = "btn btn-ghost btn-xs btn-error") {
                                            +currentStrings.cancelReservation
                                            onClick { onCancelReservation(participant) }
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

/**
 * Kdo se z lekce omluvil. Místo nedrží, takže v [ParticipantsCard] není — ale
 * omluvenku si host vzít zpět nemůže, takže se překlik opravuje jedině odsud.
 */
@Composable
fun IComponent.OptedOutCard(
    data: AdminEventDetailData,
    revokingId: Uuid?,
    onRevokeOptOut: (AdminParticipantRow) -> Unit,
) {
    val currentStrings by strings

    if (data.optedOut.isEmpty()) return

    div(className = "card bg-base-100 shadow-sm") {
        div(className = "card-body p-0") {
            div(className = "px-4 pt-4 pb-2") {
                div(className = "flex items-center gap-2") {
                    span(className = "icon-[heroicons--hand-raised] size-5 text-warning")
                    h2(className = "font-bold text-lg") { +currentStrings.optedOutSectionTitle }
                    span(className = "text-sm text-base-content/60") { +"(${data.optedOut.size})" }
                }
                p(className = "text-xs text-base-content/60 mt-1") { +currentStrings.optedOutSectionNote }
            }
            div(className = "overflow-x-auto") {
                table(className = "table table-sm w-full") {
                    thead {
                        tr {
                            th { +currentStrings.tableHeaderParticipant }
                            th { +currentStrings.tableHeaderSeats }
                            th(className = "text-right") { +currentStrings.tableHeaderActions }
                        }
                    }
                    tbody {
                        data.optedOut.forEach { participant ->
                            tr {
                                td {
                                    div(className = "font-medium") { +participant.contactName }
                                    div(className = "text-xs text-base-content/60") { +participant.contactEmail }
                                }
                                td { +"${participant.seatCount}" }
                                td(className = "text-right") {
                                    button(className = "btn btn-ghost btn-xs gap-1 tooltip tooltip-left") {
                                        attribute("data-tip", currentStrings.tooltipRevokeOptOut)
                                        disabled(revokingId != null)
                                        onClick { onRevokeOptOut(participant) }
                                        if (revokingId == participant.reservationId) {
                                            span(className = "loading loading-spinner loading-xs")
                                        } else {
                                            span(className = "icon-[heroicons--arrow-uturn-left] size-4")
                                        }
                                        +currentStrings.revokeOptOut
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
