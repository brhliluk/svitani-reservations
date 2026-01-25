package cz.svitaninymburk.projects.reservations.ui.reservation

import dev.kilua.form.InputType
import dev.kilua.form.text.text
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.copyToClipboard
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.qr.QrCodeService
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.shareQrImage
import cz.svitaninymburk.projects.reservations.shareSvgAsPng
import dev.kilua.core.IComponent
import dev.kilua.html.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IComponent.ReservationSuccessScreen(
    reservation: Reservation,
    target: ReservationTarget,
    qrCodeSvg: String,
    bankAccountNumber: String,
    onCancelReservation: () -> Unit,
    onBackToDashboard: () -> Unit
) {
    val currentStrings by strings

    div(className = "min-h-screen bg-base-200 flex items-center justify-center p-4 font-sans") {

        // Hlavní karta
        div(className = "card w-full max-w-5xl bg-base-100 shadow-xl overflow-hidden border border-base-200") {

            // --- HEADER ---
            div(className = "bg-success/10 p-8 text-center border-b border-emerald-800/50") {
                div(className = "flex justify-center mb-3") {
                    div(className = "rounded-full bg-success/20 text-emerald-400 p-4 shadow-[0_0_15px_rgba(16,185,129,0.2)]") {
                        span(className = "icon-[heroicons--check-circle] size-12")
                    }
                }
                // Nadpis čistě bílý
                h1(className = "text-3xl font-bold text-white tracking-wide drop-shadow-sm") { +"Rezervace úspěšná" }
                p(className = "text-success font-medium mt-2 uppercase tracking-wider text-sm opacity-90") { +"Čekáme na platbu" }
            }

            div(className = "card-body p-0") {
                div(className = "grid grid-cols-1 lg:grid-cols-2") {

                    // --- LEVÁ ČÁST: DETAIL REZERVACE ---
                    div(className = "p-6 lg:p-10 flex flex-col gap-6 border-b lg:border-b-0 lg:border-r border-base-200") {

                        div {
                            h2(className = "text-sm font-bold uppercase tracking-wider text-base-content/50 mb-4") { +"Souhrn rezervace" }

                            // Název akce
                            h3(className = "text-2xl font-bold text-primary mb-1") { +target.title }

                            // Datum a čas
                            div(className = "flex items-center gap-2 text-base-content/70 font-medium mb-4") {
                                span(className = "icon-[heroicons--calendar] size-5")
                                span {
                                    +"${target.startDateTime}" // TODO: Formátovat hezky
                                }
                            }
                        }

                        // Detaily (Tabulka)
                        div(className = "flex flex-col gap-3 bg-base-200/50 p-4 rounded-xl") {
                            DetailRow(currentStrings.nameLabel, reservation.contactName)
                            DetailRow(currentStrings.emailLabel, reservation.contactEmail)
                            DetailRow(currentStrings.seatCountLabel, "${reservation.seatCount} osob")

                            // Oddělovač
                            div(className = "divider my-0") {}

                            // Cena celkem
                            div(className = "flex justify-between items-center") {
                                span(className = "text-base-content/70") { +"Cena celkem" }
                                span(className = "text-xl font-bold text-primary") { +"${reservation.totalPrice} Kč" }
                            }
                        }

                        // Tlačítka akcí (Zrušit / Zpět)
                        div(className = "mt-auto pt-6 flex flex-wrap gap-4") {
                            button(className = "btn btn-outline btn-error btn-sm gap-2") {
                                onClick { onCancelReservation() }
                                span(className = "icon-[heroicons--trash] size-4")
                                +"Zrušit rezervaci"
                            }
                        }
                    }

                    // --- PRAVÁ ČÁST: PLATBA ---
                    div(className = "p-6 lg:p-10 bg-base-100 flex flex-col items-center text-center") {

                        h2(className = "text-lg font-bold mb-6 flex items-center gap-2") {
                            span(className = "icon-[heroicons--qr-code] size-6 text-primary")
                            +"Platba QR kódem"
                        }

                        // --- QR KÓD (Klikatelný) ---
                        div(className = "relative group cursor-pointer tooltip tooltip-bottom") {
                            attribute("data-tip", "Kliknutím sdílet / stáhnout") // Tooltip pro desktop

                            onClick {
                                shareSvgAsPng(qrCodeSvg)
                            }

                            div(className = "bg-white p-2 rounded-xl shadow-sm border border-base-200 mb-6") {
                                html { +qrCodeSvg }
                            }

                            // Overlay s ikonou (zobrazí se při hoveru nebo naznačuje interakci)
                            div(className = "absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity bg-base-100/80 p-3 rounded-full shadow-md pointer-events-none") {
                                span(className = "icon-[heroicons--share] size-8 text-primary")
                            }
                        }

                        // Text pod QR kódem upřesníme
                        p(className = "text-sm text-base-content/70 mb-6 max-w-xs") {
                            +"Naskenujte kód, nebo na něj "
                            span(className = "font-bold text-primary") { +"klikněte" }
                            +" pro sdílení do bankovní aplikace."
                        }

                        // Platební údaje s kopírováním (Mobile Friendly)
                        div(className = "w-full max-w-sm flex flex-col gap-3") {

                            // 1. Číslo účtu
                            CopyToClipboardButton(
                                label = "Číslo účtu",
                                value = bankAccountNumber
                            )

                            // 2. Variabilní symbol (pokud existuje, ID rezervace?)
                            CopyToClipboardButton(
                                label = "Variabilní symbol / Zpráva",
                                value = reservation.id.take(8) // Příklad
                            )
                        }
                    }
                }
            }

            // --- FOOTER ---
            div(className = "bg-base-200/50 p-4 flex justify-center border-t border-base-200") {
                button(className = "btn btn-primary w-full sm:w-auto px-10") {
                    onClick { onBackToDashboard() }
                    +"Hotovo, zpět na přehled"
                }
            }
        }
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