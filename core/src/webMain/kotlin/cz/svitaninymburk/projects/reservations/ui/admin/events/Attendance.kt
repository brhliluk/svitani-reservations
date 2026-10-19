package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.admin.AdminParticipantRow
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.util.ReservationStatusBadge
import cz.svitaninymburk.projects.reservations.ui.util.reservationStatusBadge
import cz.svitaninymburk.projects.reservations.util.PhoneNumber
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import dev.kilua.core.IComponent
import dev.kilua.html.*
import web.history.history
import web.window.window

@Composable
fun IComponent.AdminAttendanceScreen(eventId: String, isSeries: Boolean) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember(eventId, isSeries) { buildAttendanceModel(scope, eventId, isSeries) }

    LaunchedEffect(eventId, isSeries) { model.load() }

    when (val state = model.uiState) {
        is AttendanceUiState.Loading -> Loading()
        is AttendanceUiState.Error -> {
            div(className = "alert alert-error max-w-lg mx-auto mt-10") {
                span(className = "icon-[heroicons--x-circle] size-6")
                span { +state.message }
                button(className = "btn btn-sm") {
                    onClick { router.navigate("/admin") }
                    +currentStrings.backToDashboard
                }
            }
        }

        is AttendanceUiState.Success -> {
            div(className = "flex flex-col gap-6 animate-fade-in") {
                AttendanceHeader(state.data, model)
                AttendancePrintTitle(state.data)
                AttendanceTable(state.data, model.extraRows)
            }
        }
    }
}

/** Ovládání nad listinou — na papír se netiskne. */
@Composable
private fun IComponent.AttendanceHeader(data: AdminEventDetailData, model: AttendanceModel) {
    val currentStrings by strings
    div(className = "flex items-center gap-4 print:hidden") {
        button(className = "btn btn-circle btn-ghost btn-sm") {
            span(className = "icon-[heroicons--arrow-left] size-5")
            onClick { history.back() }
        }
        div(className = "flex-1") {
            h1(className = "text-2xl font-bold text-base-content flex items-center gap-2") {
                span(className = "icon-[heroicons--clipboard-document-check] text-primary size-6")
                +currentStrings.attendanceButton
            }
            p(className = "text-base-content/60 text-sm") { +data.title }
            p(className = "text-base-content/60 text-sm") { +data.subtitle }
        }

        div(className = "flex items-center gap-2") {
            span(className = "text-sm text-base-content/60") { +currentStrings.emptyRowsLabel }
            div(className = "join") {
                button(className = "join-item btn btn-sm btn-outline") {
                    onClick { model.setExtraRows(model.extraRows - 1) }
                    +"−"
                }
                span(className = "join-item btn btn-sm btn-ghost pointer-events-none min-w-10") {
                    +"${model.extraRows}"
                }
                button(className = "join-item btn btn-sm btn-outline") {
                    onClick { model.setExtraRows(model.extraRows + 1) }
                    +"+"
                }
            }
        }

        button(className = "btn btn-primary btn-sm gap-2") {
            span(className = "icon-[heroicons--printer] size-4")
            +currentStrings.printList
            onClick { window.print() }
        }
    }
}

/** Nadpis, který je vidět jen na vytištěné stránce — na obrazovce ho nese hlavička. */
@Composable
private fun IComponent.AttendancePrintTitle(data: AdminEventDetailData) {
    val currentStrings by strings
    div(className = "hidden print:block mb-4") {
        h1(className = "text-2xl font-bold") { +currentStrings.attendancePrintHeader(data.title) }
        p(className = "text-sm text-gray-600 mt-1") { +data.subtitle }
    }
}

@Composable
private fun IComponent.AttendanceTable(data: AdminEventDetailData, extraRows: Int) {
    val currentStrings by strings
    div(className = "card bg-base-100 shadow-sm print:shadow-none print:border-0") {
        div(className = "card-body p-0") {
            div(className = "overflow-x-auto") {
                table(className = "table table-zebra w-full print:text-sm") {
                    thead {
                        tr {
                            th(className = "w-10") { +"#" }
                            // Na obrazovce jméno i kontakt v jednom sloupci...
                            th(className = "print:hidden") { +currentStrings.tableHeaderParticipant }
                            // ...na papíře zvlášť, aby zbylo místo na podpis.
                            th(className = "hidden print:table-cell") { +currentStrings.nameLabel }
                            th(className = "hidden print:table-cell") { +currentStrings.phoneLabel }
                            th { +currentStrings.tableHeaderSeats }
                            th { +currentStrings.tableHeaderPaymentStatus }
                            th(className = "hidden print:table-cell w-32") { +currentStrings.tableHeaderPresence }
                        }
                    }
                    tbody {
                        if (data.participants.isEmpty() && extraRows == 0) {
                            tr {
                                td {
                                    attribute("colspan", "7")
                                    div(className = "text-center text-base-content/50 py-4 italic") {
                                        +currentStrings.noParticipants
                                    }
                                }
                            }
                        } else {
                            data.participants.forEachIndexed { index, participant ->
                                ParticipantAttendanceRow(index, participant)
                            }
                            repeat(extraRows) { i ->
                                BlankAttendanceRow(data.participants.size + i + 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IComponent.ParticipantAttendanceRow(
    index: Int,
    participant: AdminParticipantRow,
) {
    val currentStrings by strings
    val badge = reservationStatusBadge(participant.status, participant.paymentType, participant.totalPrice)

    tr {
        td(className = "text-base-content/60 text-sm") { +"${index + 1}" }

        td(className = "print:hidden") {
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
                val contact = listOfNotNull(
                    participant.contactEmail,
                    participant.contactPhone?.let { PhoneNumber.format(it) },
                ).joinToString(" • ")
                +contact
            }
        }

        td(className = "hidden print:table-cell font-bold") { +participant.contactName }
        td(className = "hidden print:table-cell text-sm") {
            +(participant.contactPhone?.let { PhoneNumber.format(it) } ?: "")
        }

        td { +"${participant.seatCount}" }

        td {
            when (badge) {
                ReservationStatusBadge.FREE -> div(className = "badge badge-success badge-outline gap-1 print:border print:border-green-600 print:bg-transparent print:text-green-700") {
                    span(className = "icon-[heroicons--gift] size-3 print:hidden")
                    +currentStrings.free
                }

                ReservationStatusBadge.PAID -> div(className = "badge badge-success gap-1 print:border print:border-green-600 print:bg-transparent print:text-green-700") {
                    span(className = "icon-[heroicons--check] size-3 print:hidden")
                    +currentStrings.paid
                }

                else -> div(className = "badge badge-warning gap-1 print:border print:border-orange-400 print:bg-transparent print:text-orange-600") {
                    span(className = "icon-[heroicons--clock] size-3 print:hidden")
                    +currentStrings.statusWaiting
                }
            }
        }

        // Místo na podpis
        td(className = "hidden print:table-cell border border-gray-300") { }
    }
}

/** Prázdný řádek pro toho, kdo přijde bez rezervace. */
@Composable
private fun IComponent.BlankAttendanceRow(rowNumber: Int) {
    tr {
        td(className = "text-base-content/60 text-sm") { +"$rowNumber" }
        td(className = "print:hidden") { }
        td(className = "hidden print:table-cell") { }
        td(className = "hidden print:table-cell") { }
        td { }
        td { }
        td(className = "hidden print:table-cell border border-gray-300") { }
    }
}
