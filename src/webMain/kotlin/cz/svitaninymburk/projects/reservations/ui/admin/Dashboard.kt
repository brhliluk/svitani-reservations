package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import dev.kilua.core.IComponent
import dev.kilua.html.*

@Composable
fun IComponent.AdminDashboardScreen() {
    div(className = "flex flex-col gap-8 animate-fade-in") {

        // Hlavička obrazovky
        div {
            h1(className = "text-3xl font-bold text-base-content") { +"Přehled" }
            p(className = "text-base-content/60 mt-1") { +"Vítejte zpět! Takhle to aktuálně vypadá s vašimi rezervacemi." }
        }

        // --- 1. KPI STATISTIKY (Horní panel) ---
        // DaisyUI 'stats' komponenta vytvoří krásný spojený panel kartiček
        div(className = "stats stats-vertical lg:stats-horizontal shadow-sm w-full bg-base-100") {

            div(className = "stat") {
                div(className = "stat-figure text-primary") {
                    span(className = "icon-[heroicons--users] size-8")
                }
                div(className = "stat-title") { +"Dnešní účastníci" }
                div(className = "stat-value text-primary") { +"24" }
                div(className = "stat-desc") { +"Ve 3 událostech" }
            }

            div(className = "stat") {
                div(className = "stat-figure text-warning") {
                    span(className = "icon-[heroicons--banknotes] size-8")
                }
                div(className = "stat-title") { +"Čeká na platbu" }
                div(className = "stat-value text-warning") { +"15 400 Kč" }
                div(className = "stat-desc") { +"Celkem 12 rezervací" }
            }

            div(className = "stat") {
                div(className = "stat-figure text-info") {
                    span(className = "icon-[heroicons--ticket] size-8")
                }
                div(className = "stat-title") { +"Volná místa" }
                div(className = "stat-value text-info") { +"18" }
                div(className = "stat-desc") { +"Na akcích v tomto týdnu" }
            }
        }

        // --- 2. HLAVNÍ OBSAH (Dva sloupce) ---
        div(className = "grid grid-cols-1 lg:grid-cols-2 gap-8") {

            // LEVÝ SLOUPEC: Nejbližší události s Progress Bary
            div(className = "card bg-base-100 shadow-sm") {
                div(className = "card-body p-6") {
                    div(className = "flex justify-between items-center mb-4") {
                        h2(className = "card-title text-lg") { +"Nejbližší události" }
                        button(className = "btn btn-sm btn-ghost") { +"Zobrazit kalendář" }
                    }

                    div(className = "flex flex-col gap-4") {
                        // Ukázkové položky (Později sem dáme forEach z backendu)
                        AdminUpcomingEventRow("Jóga pro zdravá záda", "Dnes 18:00", 10, 12)
                        AdminUpcomingEventRow("Keramická dílna pro děti", "Zítra 15:00", 8, 8)
                        AdminUpcomingEventRow("Soukromá oslava (Herna)", "Sobota 10:00", 1, 1)
                    }
                }
            }

            // PRAVÝ SLOUPEC: Akce vyžadující pozornost (Neuhrazené rezervace)
            div(className = "card bg-base-100 shadow-sm") {
                div(className = "card-body p-6") {
                    div(className = "flex justify-between items-center mb-4") {
                        h2(className = "card-title text-lg") { +"Poslední neuhrazené rezervace" }
                        button(className = "btn btn-sm btn-ghost text-warning") { +"Zobrazit všechny" }
                    }

                    div(className = "flex flex-col gap-3") {
                        AdminPendingReservationRow("Jana Nováková", "Jóga pro zdravá záda", "360 Kč", "VS: 2605412345")
                        AdminPendingReservationRow("Petr Svoboda", "Keramická dílna", "2 200 Kč", "VS: 2605498765")
                        AdminPendingReservationRow("Lucie Bílá", "Soukromá oslava", "3 000 Kč", "VS: 2605455555")
                    }
                }
            }
        }
    }
}

// --- POMOCNÉ KOMPONENTY PRO ŘÁDKY ---

@Composable
fun IComponent.AdminUpcomingEventRow(title: String, time: String, occupied: Int, capacity: Int) {
    val isFull = occupied >= capacity
    val progressClass = if (isFull) "progress-error" else if (occupied.toDouble() / capacity > 0.8) "progress-warning" else "progress-success"

    div(className = "flex flex-col gap-2 p-3 bg-base-200/50 rounded-lg hover:bg-base-200 transition-colors cursor-pointer") {
        div(className = "flex justify-between items-start") {
            div {
                div(className = "font-bold text-sm") { +title }
                div(className = "text-xs text-base-content/60 mt-1 flex items-center gap-1") {
                    span(className = "icon-[heroicons--clock] size-3")
                    +time
                }
            }
            if (isFull) {
                div(className = "badge badge-error badge-sm font-bold") { +"PLNO" }
            } else {
                div(className = "text-xs font-bold text-base-content/70") { +"$occupied / $capacity" }
            }
        }
        // DaisyUI progress bar
        progress(className = "progress $progressClass w-full h-2") {
            attribute("value", occupied.toString())
            attribute("max", capacity.toString())
        }
    }
}

@Composable
fun IComponent.AdminPendingReservationRow(name: String, eventName: String, price: String, vs: String) {
    div(className = "flex justify-between items-center p-3 border border-base-200 rounded-lg hover:border-warning/50 transition-colors") {
        div(className = "flex flex-col") {
            span(className = "font-bold text-sm") { +name }
            span(className = "text-xs text-base-content/60 truncate max-w-[150px] sm:max-w-[200px]") { +eventName }
            span(className = "text-xs font-mono text-base-content/40 mt-1") { +vs }
        }
        div(className = "flex items-center gap-3") {
            span(className = "font-bold text-warning") { +price }
            button(className = "btn btn-circle btn-ghost btn-sm text-success") {
                attribute("title", "Označit jako zaplacené")
                span(className = "icon-[heroicons--check] size-5")
            }
        }
    }
}