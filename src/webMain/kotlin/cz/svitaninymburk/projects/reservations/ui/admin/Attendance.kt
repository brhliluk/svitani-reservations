package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import dev.kilua.core.IComponent
import dev.kilua.html.*
import dev.kilua.rpc.getService
import web.history.history
import kotlin.js.js
import kotlin.uuid.Uuid

private sealed interface AttendanceUiState {
    data object Loading : AttendanceUiState
    data class Success(val data: AdminEventDetailData) : AttendanceUiState
    data class Error(val message: String) : AttendanceUiState
}

@Composable
fun IComponent.AdminAttendanceScreen(eventId: String, isSeries: Boolean) {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val currentStrings by strings

    var extraRows by remember { mutableStateOf(5) }

    val uiState by produceState<AttendanceUiState>(initialValue = AttendanceUiState.Loading) {
        try {
            val uuid = Uuid.parse(eventId)
            adminService.getEventDetail(uuid, isSeries)
                .onRight { value = AttendanceUiState.Success(it) }
                .onLeft { value = AttendanceUiState.Error(it.localizedMessage) }
        } catch (e: IllegalArgumentException) {
            value = AttendanceUiState.Error(currentStrings.invalidEventId)
        }
    }

    when (val state = uiState) {
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
            val data = state.data
            val prefix = if (isSeries) "/admin/events/series" else "/admin/events/instance"

            div(className = "flex flex-col gap-6 animate-fade-in") {

                // --- HEADER (hidden on print) ---
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

                    // Extra rows control
                    div(className = "flex items-center gap-2") {
                        span(className = "text-sm text-base-content/60") { +currentStrings.emptyRowsLabel }
                        div(className = "join") {
                            button(className = "join-item btn btn-sm btn-outline") {
                                onClick { if (extraRows > 0) extraRows-- }
                                +"−"
                            }
                            span(className = "join-item btn btn-sm btn-ghost pointer-events-none min-w-10") {
                                +"$extraRows"
                            }
                            button(className = "join-item btn btn-sm btn-outline") {
                                onClick { if (extraRows < 20) extraRows++ }
                                +"+"
                            }
                        }
                    }

                    // Print button
                    button(className = "btn btn-primary btn-sm gap-2") {
                        span(className = "icon-[heroicons--printer] size-4")
                        +currentStrings.printList
                        onClick { js("window.print()") }
                    }
                }

                // --- PRINT-ONLY TITLE BLOCK ---
                div(className = "hidden print:block mb-4") {
                    h1(className = "text-2xl font-bold") { +currentStrings.attendancePrintHeader(data.title) }
                    p(className = "text-sm text-gray-600 mt-1") { +data.subtitle }
                }

                // --- PARTICIPANTS TABLE ---
                div(className = "card bg-base-100 shadow-sm print:shadow-none print:border-0") {
                    div(className = "card-body p-0") {
                        div(className = "overflow-x-auto") {
                            table(className = "table table-zebra w-full print:text-sm") {
                                thead {
                                    tr {
                                        th(className = "w-10") { +"#" }
                                        // Screen: combined name + contact
                                        th(className = "print:hidden") { +currentStrings.tableHeaderParticipant }
                                        // Print only: name and phone in separate columns
                                        th(className = "hidden print:table-cell") { +currentStrings.nameLabel }
                                        th(className = "hidden print:table-cell") { +currentStrings.phoneLabel }
                                        th { +currentStrings.tableHeaderSeats }
                                        th { +currentStrings.tableHeaderPaymentStatus }
                                        // Print only: presence/signature column
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
                                            val isPaid = participant.status == Reservation.Status.CONFIRMED

                                            tr {
                                                td(className = "text-base-content/60 text-sm") { +"${index + 1}" }

                                                // Screen: combined name + email + phone
                                                td(className = "print:hidden") {
                                                    div(className = "font-bold") { +participant.contactName }
                                                    div(className = "text-xs text-base-content/50") {
                                                        val contact = listOfNotNull(participant.contactEmail, participant.contactPhone).joinToString(" • ")
                                                        +contact
                                                    }
                                                }

                                                // Print only: name
                                                td(className = "hidden print:table-cell font-bold") { +participant.contactName }
                                                // Print only: phone
                                                td(className = "hidden print:table-cell text-sm") {
                                                    +(participant.contactPhone ?: "")
                                                }

                                                td { +"${participant.seatCount}" }

                                                // Payment status
                                                td {
                                                    if (isPaid) {
                                                        div(className = "badge badge-success gap-1 print:border print:border-green-600 print:bg-transparent print:text-green-700") {
                                                            span(className = "icon-[heroicons--check] size-3 print:hidden")
                                                            +currentStrings.paid
                                                        }
                                                    } else {
                                                        div(className = "badge badge-warning gap-1 print:border print:border-orange-400 print:bg-transparent print:text-orange-600") {
                                                            span(className = "icon-[heroicons--clock] size-3 print:hidden")
                                                            +currentStrings.statusWaiting
                                                        }
                                                    }
                                                }

                                                // Print only: signature cell
                                                td(className = "hidden print:table-cell border border-gray-300") { }
                                            }
                                        }

                                        // Extra blank rows
                                        repeat(extraRows) { i ->
                                            val rowNum = data.participants.size + i + 1
                                            tr {
                                                td(className = "text-base-content/60 text-sm") { +"$rowNum" }
                                                // Screen: single empty name column
                                                td(className = "print:hidden") { }
                                                // Print: separate name + phone columns
                                                td(className = "hidden print:table-cell") { }
                                                td(className = "hidden print:table-cell") { }
                                                td { }
                                                td { }
                                                td(className = "hidden print:table-cell border border-gray-300") { }
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
