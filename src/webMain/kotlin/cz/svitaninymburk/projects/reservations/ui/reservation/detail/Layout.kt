package cz.svitaninymburk.projects.reservations.ui.reservation.detail

import dev.kilua.form.InputType
import dev.kilua.form.text.text
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.copyToClipboard
import cz.svitaninymburk.projects.reservations.qr.QrCodeService
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.shareSvgAsPng
import dev.kilua.core.IComponent
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun IComponent.ReservationDetailLayout(
    reservation: Reservation,
    target: ReservationTarget,
    onCancelReservation: () -> Unit,
    onBackToDashboard: () -> Unit
) {
    val uiState = remember(reservation.status) { getReservationUiState(reservation) }
    val qrCodeService = getService<QrCodeService>()

    val qrCodeSvg = remember(reservation, uiState.showPaymentInfo) {
        if (uiState.showPaymentInfo) qrCodeService.generateReservationPaymentSvg(reservation)
        else ""
    }

    div(className = "min-h-screen bg-base-200 flex items-center justify-center p-4 font-sans") {

        div(className = "card w-full max-w-5xl bg-base-100 shadow-xl overflow-hidden") {

            div(className = "${uiState.headerBgClass} p-8 text-center border-b border-base-200 transition-colors duration-300") {
                div(className = "flex justify-center mb-3") {
                    div(className = "rounded-full p-4 ${uiState.iconBgClass}") {
                        span(className = "size-12 ${uiState.iconClass}")
                    }
                }
                h1(className = "text-3xl font-bold text-base-content tracking-wide") { +uiState.title }
                p(className = "${uiState.textColorClass} font-bold mt-2 uppercase tracking-wider text-sm opacity-90") { +uiState.subtitle }
            }

            div(className = "card-body p-0") {
                div(className = "grid grid-cols-1 lg:grid-cols-2") {

                    // --- LEVÁ ČÁST: DETAIL ---
                    div(className = "p-8 lg:p-12 flex flex-col gap-6 border-b lg:border-b-0 lg:border-r border-base-200") {

                        // ... (Zobrazení detailů rezervace - stejné jako minule) ...
                        div {
                            h2(className = "text-xs font-bold uppercase tracking-widest text-base-content/50 mb-2") { +"Souhrn rezervace" }
                            h3(className = "text-3xl font-bold text-primary mb-2") { +target.title }
                            div(className = "flex items-center gap-2 text-base-content/70 font-medium") {
                                span(className = "icon-[heroicons--calendar] size-5")
                                span { +"${target.startDateTime}" }
                            }
                        }

                        div(className = "flex flex-col gap-4 bg-base-200/50 p-6 rounded-xl") {
                            DetailRow("Stav", uiState.statusLabel) // Zobrazíme textový stav
                            DetailRow("Jméno", reservation.contactName)
                            DetailRow("Cena celkem", "${reservation.totalPrice} Kč")
                        }

                        // Tlačítko Zrušit (jen pokud to dává smysl)
                        if (uiState.canBeCancelled) {
                            div(className = "mt-auto pt-4") {
                                button(className = "btn btn-outline btn-error btn-sm gap-2") {
                                    onClick { onCancelReservation() }
                                    span(className = "icon-[heroicons--trash] size-4")
                                    +"Zrušit rezervaci"
                                }
                            }
                        }
                    }

                    // --- PRAVÁ ČÁST: PLATBA / INFO ---
                    div(className = "p-8 lg:p-12 flex flex-col items-center text-center justify-center") {

                        if (uiState.showPaymentInfo) {
                            // === ZOBRAZIT QR KÓD (Čeká se na platbu) ===
                            h2(className = "text-lg font-bold mb-8 flex items-center gap-3 text-base-content") {
                                span(className = "icon-[heroicons--qr-code] size-6 text-primary")
                                +"Platba QR kódem"
                            }

                            div(className = "relative group cursor-pointer tooltip tooltip-bottom") {
                                attribute("data-tip", "Kliknutím sdílet / stáhnout")
                                onClick { shareSvgAsPng(qrCodeSvg) }

                                div(className = "bg-white p-3 rounded-xl shadow-sm border border-base-300 mb-6 transition-transform group-hover:scale-105 duration-200") {
                                    div("w-48 h-48") {
                                        html { +qrCodeSvg }
                                    }
                                }
                                // ... overlay ...
                            }

                            div(className = "w-full max-w-sm flex flex-col gap-4") {
                                CopyToClipboardButton("Číslo účtu", qrCodeService.accountNumber)
                                CopyToClipboardButton("VS", reservation.variableSymbol ?: "---")
                            }

                        } else {
                            // === NEZOBRAZOVAT PLATBU (Zaplaceno nebo Zrušeno) ===
                            div(className = "opacity-50 flex flex-col items-center gap-4") {
                                span(className = "size-24 ${uiState.iconClass}")
                                p(className = "text-xl font-medium") {
                                    if (reservation.status == Reservation.Status.CANCELLED) "Tato rezervace je zrušena."
                                    else "Vše je uhrazeno. Těšíme se na vás!"
                                }
                            }
                        }
                    }
                }
            }

            // --- FOOTER ---
            div(className = "bg-base-200/50 p-6 flex justify-center border-t border-base-200") {
                button(className = "btn btn-primary w-full sm:w-auto px-12 font-bold text-lg shadow-lg") {
                    onClick { onBackToDashboard() }
                    +"Zpět na přehled"
                }
            }
        }
    }
}

// --- LOGIKA STAVŮ (Configuration) ---

private data class ReservationUiState(
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val headerBgClass: String, // Pozadí hlavičky
    val iconBgClass: String,   // Pozadí kolečka pod ikonou
    val iconClass: String,     // Samotná ikona (CSS třída)
    val textColorClass: String,// Barva textu v hlavičce
    val showPaymentInfo: Boolean,
    val canBeCancelled: Boolean
)

private fun getReservationUiState(reservation: Reservation): ReservationUiState {
    return when (reservation.status) {
        // 1. NOVÁ / ČEKÁ NA PLATBU
        Reservation.Status.PENDING_PAYMENT -> ReservationUiState(
            title = "Rezervace vytvořena",
            subtitle = "Čekáme na platbu",
            statusLabel = "Nezaplaceno",
            headerBgClass = "bg-warning/10", // Žlutá/Oranžová pro "Attention"
            iconBgClass = "bg-warning/20 text-warning",
            iconClass = "icon-[heroicons--clock] text-warning",
            textColorClass = "text-warning",
            showPaymentInfo = true,
            canBeCancelled = true
        )
        // 2. POTVRZENÁ / ZAPLACENÁ
        Reservation.Status.CONFIRMED -> ReservationUiState(
            title = "Rezervace potvrzena",
            subtitle = "Vše je v pořádku",
            statusLabel = "Zaplaceno",
            headerBgClass = "bg-success/10", // Zelená
            iconBgClass = "bg-success/20 text-success",
            iconClass = "icon-[heroicons--check-circle] text-success",
            textColorClass = "text-success",
            showPaymentInfo = false, // Už neukazujeme QR kód (nebo ho můžeme ukázat jako "daňový doklad")
            canBeCancelled = true // Stále lze stornovat (s vratkou)
        )
        // 3. ZRUŠENÁ
        Reservation.Status.CANCELLED -> ReservationUiState(
            title = "Rezervace zrušena",
            subtitle = "Tato rezervace není platná",
            statusLabel = "Stornováno",
            headerBgClass = "bg-error/10", // Červená
            iconBgClass = "bg-error/20 text-error",
            iconClass = "icon-[heroicons--x-circle] text-error",
            textColorClass = "text-error",
            showPaymentInfo = false, // Skryjeme platbu
            canBeCancelled = false // Už nejde zrušit znovu
        )
        Reservation.Status.REJECTED -> ReservationUiState(
            title = "Rezervace zamítnuta",
            subtitle = "Organizátor rezervaci neschválil",
            statusLabel = "Zamítnuto",
            headerBgClass = "bg-error/10", // Také červená, je to "chyba/konec"
            iconBgClass = "bg-error/20 text-error",
            iconClass = "icon-[heroicons--x-circle] text-error", // Ikona křížku/zákazu
            textColorClass = "text-error",
            showPaymentInfo = false, // Rozhodně neukazovat QR kód
            canBeCancelled = false
        )
    }
}

// --- POMOCNÉ KOMPONENTY ---

@Composable
fun IComponent.DetailRow(label: String, value: String) {
    div(className = "flex justify-between text-sm") {
        span(className = "text-base-content/60") { +label }
        span(className = "font-medium text-base-content") { +value }
    }
}

@Composable
fun IComponent.CopyToClipboardButton(label: String, value: String) {
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    div(className = "form-control w-full") {
        label(className = "label pb-1") {
            span(className = "label-text text-xs uppercase font-bold text-base-content/50") { +label }
        }
        div(className = "join w-full shadow-sm") {
            // Hodnota (Readonly input)
            text(type = InputType.Text, value = value, className = "input input-bordered join-item w-full bg-base-100 text-sm font-mono focus:outline-none") {
                readonly(true)
            }

            // Tlačítko Kopírovat
            button(className = "btn btn-neutral join-item") {
                onClick {
                    copyToClipboard(value)
                    isCopied = true
                    scope.launch {
                        delay(2.seconds)
                        isCopied = false
                    }
                }

                if (isCopied) {
                    span(className = "icon-[heroicons--check] size-5 text-success")
                    span(className = "hidden sm:inline text-success") { +"Zkopírováno" }
                } else {
                    span(className = "icon-[heroicons--document-duplicate] size-5")
                }
            }
        }
    }
}
