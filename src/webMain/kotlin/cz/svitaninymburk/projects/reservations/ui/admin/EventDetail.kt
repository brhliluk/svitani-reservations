package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

// UI Stavy
private sealed interface AdminEventDetailUiState {
    data object Loading : AdminEventDetailUiState
    data class Success(val data: AdminEventDetailData) : AdminEventDetailUiState
    data class Error(val message: String) : AdminEventDetailUiState
}

@Composable
fun IComponent.AdminEventDetailScreen(eventId: String, isSeries: Boolean) {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()

    var refreshTrigger by remember { mutableStateOf(0) }
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    // Načítání dat z backendu
    val uiState by produceState<AdminEventDetailUiState>(initialValue = AdminEventDetailUiState.Loading, key1 = refreshTrigger) {
        try {
            val uuid = Uuid.parse(eventId)
            adminService.getEventDetail(uuid, isSeries)
                .onRight { value = AdminEventDetailUiState.Success(it) }
                .onLeft { value = AdminEventDetailUiState.Error(it.localizedMessage) }
        } catch (e: IllegalArgumentException) {
            value = AdminEventDetailUiState.Error("Neplatné ID události.")
        }
    }

    when (val state = uiState) {
        is AdminEventDetailUiState.Loading -> Loading()
        is AdminEventDetailUiState.Error -> {
            div(className = "alert alert-error max-w-lg mx-auto mt-10") {
                span(className = "icon-[heroicons--x-circle] size-6")
                span { +state.message }
                button(className = "btn btn-sm") {
                    onClick { router.navigate("/admin") }
                    +"Zpět na přehled"
                }
            }
        }
        is AdminEventDetailUiState.Success -> {
            val data = state.data

            div(className = "flex flex-col gap-6 animate-fade-in") {

                // --- 1. HLAVIČKA A TLAČÍTKO ZPĚT ---
                div(className = "flex items-center gap-4") {
                    button(className = "btn btn-circle btn-ghost btn-sm") {
                        span(className = "icon-[heroicons--arrow-left] size-5")
                        onClick { router.navigate("/admin") }
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

                // --- 2. STATISTIKY ---
                div(className = "stats shadow-sm bg-base-100 w-full") {
                    div(className = "stat") {
                        div(className = "stat-title") { +"Obsazenost" }
                        val isFull = data.occupiedSpots >= data.capacity
                        div(className = "stat-value ${if (isFull) "text-error" else "text-primary"}") {
                            +"${data.occupiedSpots} / ${data.capacity}"
                        }
                        div(className = "stat-desc") {
                            if (isFull) +"Kapacita naplněna" else +"Zbývá ${data.capacity - data.occupiedSpots} míst"
                        }
                    }
                    div(className = "stat") {
                        div(className = "stat-title") { +"Vybráno (Potvrzené)" }
                        div(className = "stat-value text-success") { +"${data.totalCollected} Kč" }
                        div(className = "stat-desc") { +"Celkem od zaplacených" }
                    }
                }

                // --- 3. TABULKA ÚČASTNÍKŮ ---
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body p-0") {
                        div(className = "overflow-x-auto") {
                            table(className = "table table-zebra w-full") {
                                thead {
                                    tr {
                                        th { +"Účastník" }
                                        th { +"Místa" }
                                        th { +"Cena" }
                                        th { +"Stav platby" }
                                        th(className = "text-right") { +"Akce" }
                                    }
                                }
                                tbody {
                                    if (data.participants.isEmpty()) {
                                        tr {
                                            td {
                                                attribute("colspan", "5")
                                                div(className = "text-center text-base-content/50 py-4 italic") {
                                                    +"Zatím žádní přihlášení účastníci."
                                                }
                                            }
                                        }
                                    } else {
                                        data.participants.forEach { participant ->

                                            // Určení vzhledu řádku podle stavu a typu platby
                                            val isPaid = participant.status == Reservation.Status.CONFIRMED // Přizpůsob svému stavu
                                            val isCash = participant.paymentType == PaymentInfo.Type.ON_SITE // Přizpůsob svému enumu

                                            tr(className = if (!isPaid && isCash) "bg-info/5" else "") {
                                                td {
                                                    div(className = "font-bold") { +participant.contactName }
                                                    div(className = "text-xs text-base-content/50") {
                                                        +"${participant.contactEmail} • ${participant.contactPhone}"
                                                    }
                                                }
                                                td { +"${participant.seatCount}" }
                                                td(className = if (!isPaid && isCash) "font-bold text-info" else "") {
                                                    +"${participant.totalPrice} Kč"
                                                }
                                                td {
                                                    div(className = "flex flex-col gap-1 items-start") {
                                                        if (isPaid) {
                                                            div(className = "badge badge-success gap-1") {
                                                                span(className = "icon-[heroicons--check] size-3")
                                                                +"Zaplaceno"
                                                            }
                                                        } else if (isCash) {
                                                            div(className = "badge badge-info badge-outline gap-1") {
                                                                span(className = "icon-[heroicons--banknotes] size-3")
                                                                +"Na místě"
                                                            }
                                                        } else {
                                                            div(className = "badge badge-warning gap-1") {
                                                                span(className = "icon-[heroicons--clock] size-3")
                                                                +"Čeká"
                                                            }
                                                        }

                                                        span(className = "text-xs text-base-content/60 font-medium") {
                                                            if (isCash) +"Hotově" else +"Převodem"
                                                        }
                                                    }
                                                }
                                                td(className = "text-right") {
                                                    div(className = "flex justify-end gap-1") {

                                                        // Tlačítko pro potvrzení platby (ukazujeme jen u nezaplacených)
                                                        if (!isPaid) {
                                                            button(className = "btn btn-xs tooltip tooltip-left ${if (isCash) "btn-outline btn-info" else "btn-ghost text-success"}") {
                                                                attribute("data-tip", if (isCash) "Přijmout hotovost" else "Označit jako zaplacené")
                                                                onClick {
                                                                    scope.launch {
                                                                        adminService.markReservationAsPaid(participant.reservationId)
                                                                            .onRight {
                                                                                toastData = ToastData("Platba od ${participant.contactName} potvrzena!", ToastType.Success)
                                                                                refreshTrigger++
                                                                            }
                                                                            .onLeft { error ->
                                                                                toastData = ToastData("Chyba: $error", ToastType.Error)
                                                                            }
                                                                    }
                                                                }
                                                                span(className = "icon-[heroicons--check-circle] size-5")
                                                                if (isCash) +"Vybrat"
                                                            }
                                                        }

                                                        // Tlačítko zrušit (Zatím jen vizuální, můžeme napojit na cancelReservation)
                                                        button(className = "btn btn-ghost btn-xs text-error tooltip tooltip-left") {
                                                            attribute("data-tip", "Zrušit rezervaci")
                                                            span(className = "icon-[heroicons--trash] size-5")
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

    // Náš globální Toast
    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}