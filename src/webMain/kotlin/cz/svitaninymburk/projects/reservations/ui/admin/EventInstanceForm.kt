package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AuthenticatedEventServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.number.numeric
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.*
import dev.kilua.rpc.getService
import cz.svitaninymburk.projects.reservations.event.generateRecurrenceDates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import web.html.HTMLSelectElement
import kotlin.js.js
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid


@Composable
fun IComponent.AdminCreateEventInstanceScreen(preselectedDefinitionId: String? = null) {
    val router = Router.current

    val eventService = getService<EventServiceInterface>(RpcSerializersModules)
    val authEventService = getService<AuthenticatedEventServiceInterface>(RpcSerializersModules)

    val scope = rememberCoroutineScope()
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    var definitions by remember { mutableStateOf<List<EventDefinition>>(emptyList()) }
    var isLoadingDefinitions by remember { mutableStateOf(true) }

    var selectedDefinitionId by remember { mutableStateOf(preselectedDefinitionId) }

    var startDate by remember { mutableStateOf("") } // Formát: YYYY-MM-DD
    var startTime by remember { mutableStateOf("") } // Formát: HH:mm

    var titleOverride by remember { mutableStateOf("") }
    var descriptionOverride by remember { mutableStateOf("") }
    var priceOverride by remember { mutableStateOf<Number?>(0) }
    var capacityOverride by remember { mutableIntStateOf(10) }
    var durationHours by remember { mutableIntStateOf(1) }
    var durationMinutes by remember { mutableIntStateOf(0) }
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }

    val selectedDefinition = definitions.find { it.id.toString() == selectedDefinitionId }
    val isRecurring = selectedDefinition?.recurrenceType != null
        && selectedDefinition.recurrenceType != RecurrenceType.NONE
        && selectedDefinition.recurrenceEndDate != null

    val previewDates: List<LocalDateTime> = remember(startDate, startTime, selectedDefinition) {
        val def = selectedDefinition ?: return@remember emptyList()
        if (!isRecurring || startDate.isBlank() || startTime.isBlank()) return@remember emptyList()
        val d = try { LocalDate.parse(startDate) } catch (e: Exception) { return@remember emptyList() }
        val t = try { LocalTime.parse(startTime) } catch (e: Exception) { return@remember emptyList() }
        generateRecurrenceDates(d, t, def.recurrenceType, def.recurrenceEndDate!!)
    }

    // Funkce pro předvyplnění formuláře při změně šablony
    fun applyDefinitionDefaults(definition: EventDefinition) {
        titleOverride = definition.title
        descriptionOverride = definition.description
        priceOverride = definition.defaultPrice
        capacityOverride = definition.defaultCapacity

        definition.defaultDuration.toComponents { hours, minutes, _, _ ->
            durationHours = hours.toInt()
            durationMinutes = minutes
        }

        allowBankTransfer = definition.allowedPaymentTypes.contains(PaymentInfo.Type.BANK_TRANSFER)
        allowOnSite = definition.allowedPaymentTypes.contains(PaymentInfo.Type.ON_SITE)
    }

    LaunchedEffect(Unit) {
        eventService.getAllDefinitions()
            .onRight { defs ->
                definitions = defs
                isLoadingDefinitions = false
                if (preselectedDefinitionId != null) {
                    defs.find { it.id.toString() == preselectedDefinitionId }?.let { applyDefinitionDefaults(it) }
                }
            }
            .onLeft { error ->
                toastData = ToastData("Chyba při načítání šablon: $error", ToastType.Error)
                isLoadingDefinitions = false
            }
    }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

        // Hlavička
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { js("window.history.back()") }
            }
            div {
                h1(className = "text-3xl font-bold text-base-content") { +"Vypsat nový termín" }
                p(className = "text-base-content/60") { +"Vyberte šablonu, nastavte datum a čas. Údaje můžete pro tento termín libovolně upravit." }
            }
        }

        if (isLoadingDefinitions) {
            div(className = "flex justify-center p-10") {
                span(className = "loading loading-spinner loading-lg text-primary")
            }
        } else if (definitions.isEmpty()) {
            div(className = "alert alert-warning shadow-sm") {
                span(className = "icon-[heroicons--exclamation-triangle] size-6")
                div {
                    h3(className = "font-bold") { +"Žádné šablony" }
                    div(className = "text-sm") { +"Než vypíšete termín, musíte vytvořit alespoň jednu šablonu události." }
                }
                button(className = "btn btn-sm btn-primary") {
                    onClick { router.navigate("/admin/events/new-definition") }
                    +"Vytvořit šablonu"
                }
            }
        } else {
            // --- VÝBĚR ŠABLONY A TERMÍNU ---
            div(className = "card bg-base-100 shadow-sm border-t-4 border-primary") {
                div(className = "card-body") {

                    div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                        // Výběr šablony
                        if (preselectedDefinitionId == null) {
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-bold") { +"Ze které šablony vycházíme?" } }
                                select(className = "select select-bordered w-full text-base") {
                                    // Výchozí prázdná volba
                                    option(value = "", label = "-- Vyberte šablonu --") {
                                        if (selectedDefinitionId == null) attribute("selected", "true")
                                        attribute("disabled", "true")
                                    }
                                    definitions.forEach { def ->
                                        option(value = def.id.toString(), label = def.title)
                                    }
                                    onChange { event ->
                                        val targetSelect = event.target as? HTMLSelectElement
                                        val selectedValue = targetSelect?.value
                                        selectedDefinitionId = selectedValue
                                        definitions.find { it.id.toString() == selectedValue }?.let { applyDefinitionDefaults(it) }
                                    }
                                }
                            }
                        }

                        // Datum
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +"Datum konání" } }
                            text(value = startDate, type = InputType.Date, className = "input input-bordered w-full") {
                                onInput { startDate = value ?: "" }
                            }
                        }

                        // Čas
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +"Čas začátku" } }
                            text(value = startTime, type = InputType.Time, className = "input input-bordered w-full") {
                                onInput { startTime = value ?: "" }
                            }
                        }
                    }
                }
            }

            // --- MOŽNOST PŘEPSÁNÍ HODNOT (Zobrazí se až po výběru šablony) ---
            if (selectedDefinitionId != null) {
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-2") { +"Úpravy pro tento konkrétní termín" }
                        p(className = "text-sm text-base-content/60 mb-4") {
                            +"Tato pole jsou předvyplněná podle šablony. Pokud je změníte, změna se projeví pouze u tohoto jednoho termínu."
                        }

                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            // Název
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +"Název termínu" } }
                                text(value = titleOverride, className = "input input-bordered w-full") {
                                    onInput { titleOverride = value ?: "" }
                                }
                            }

                            // Popis
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +"Popis" } }
                                textArea(value = descriptionOverride, className = "textarea textarea-bordered h-24 w-full") {
                                    onInput { descriptionOverride = value ?: "" }
                                }
                            }

                            // Cena a Kapacita (Stejný vzhled jako u Definice)
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +"Cena" } }
                                div(className = "relative flex items-center") {
                                    numeric(value = priceOverride, min = 0, className = "input input-bordered w-full pr-12") {
                                        onInput { priceOverride = value }
                                    }
                                    span(className = "absolute right-4 text-base-content/50 font-medium") { +"Kč" }
                                }
                            }

                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +"Kapacita osob" } }
                                div(className = "relative flex items-center") {
                                    numeric(value = capacityOverride, min = 1, decimals = 0, className = "input input-bordered w-full pr-12") {
                                        attribute("step", "1")
                                        onInput { capacityOverride = value?.toInt() ?: 1 }
                                    }
                                    span(className = "absolute right-4 text-base-content/50") {
                                        span(className = "icon-[heroicons--users] size-5")
                                    }
                                }
                            }

                            // Délka
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +"Délka" } }
                                div(className = "flex gap-2") {
                                    div(className = "relative flex-1 items-center flex") {
                                        numeric(value = durationHours, min = 0, decimals = 0, className = "input input-bordered w-full pr-8") {
                                            attribute("step", "1")
                                            onInput { durationHours = value?.toInt() ?: 0 }
                                        }
                                        span(className = "absolute right-3 text-base-content/50 text-sm") { +"h" }
                                    }
                                    div(className = "relative flex-1 items-center flex") {
                                        numeric(value = durationMinutes, min = 0, max = 59, decimals = 0, className = "input input-bordered w-full pr-12") {
                                            attribute("step", "1")
                                            onInput { durationMinutes = value?.toInt() ?: 0 }
                                        }
                                        span(className = "absolute right-3 text-base-content/50 text-sm") { +"min" }
                                    }
                                }
                            }

                            // Platby
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +"Povolené platby" } }
                                div(className = "flex gap-4 mt-2") {
                                    label(className = "cursor-pointer label justify-start gap-2") {
                                        checkBox(value = allowBankTransfer, className = "checkbox checkbox-primary") {
                                            onChange { allowBankTransfer = value }
                                        }
                                        span(className = "label-text") { +"Převodem" }
                                    }
                                    label(className = "cursor-pointer label justify-start gap-2") {
                                        checkBox(value = allowOnSite, className = "checkbox checkbox-primary") {
                                            onChange { allowOnSite = value }
                                        }
                                        span(className = "label-text") { +"Na místě" }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- NÁHLED OPAKOVÁNÍ ---
                if (isRecurring && startDate.isNotBlank() && startTime.isNotBlank()) {
                    div(className = "card bg-base-100 shadow-sm border border-primary/30") {
                        div(className = "card-body") {
                            div(className = "flex items-center gap-2 mb-3") {
                                span(className = "icon-[heroicons--calendar-days] size-5 text-primary")
                                h2(className = "card-title text-base") {
                                    +"Náhled termínů (${previewDates.size})"
                                }
                            }
                            if (previewDates.isEmpty()) {
                                p(className = "text-warning text-sm") { +"Datum konce je před datem začátku. Žádné termíny nevzniknou." }
                            } else {
                                div(className = "flex flex-wrap gap-2") {
                                    previewDates.forEach { dt ->
                                        val min = dt.minute.toString().padStart(2, '0')
                                        span(className = "badge badge-outline badge-primary") {
                                            +"${dt.date.dayOfMonth}.${dt.date.monthNumber}.${dt.date.year} ${dt.hour}:$min"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- ULOŽIT ---
                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") {
                        onClick { js("window.history.back()") }
                        +"Zrušit"
                    }
                    button(className = "btn btn-primary") {
                        onClick {
                            if (startDate.isBlank() || startTime.isBlank()) {
                                toastData = ToastData("Musíte vybrat datum a čas konání.", ToastType.Error)
                                return@onClick
                            }

                            // Spojíme datum a čas do ISO formátu, který schroupne LocalDateTime
                            val isoDateTimeString = "${startDate}T${startTime}"
                            val parsedDateTime = try {
                                LocalDateTime.parse(isoDateTimeString)
                            } catch (e: Exception) {
                                toastData = ToastData("Neplatný formát data nebo času.", ToastType.Error)
                                return@onClick
                            }

                            // Připravíme platby
                            val allowedPayments = mutableListOf<PaymentInfo.Type>()
                            if (allowBankTransfer) allowedPayments.add(PaymentInfo.Type.BANK_TRANSFER)
                            if (allowOnSite) allowedPayments.add(PaymentInfo.Type.ON_SITE)

                            val baseRequest = CreateEventInstanceRequest(
                                definitionId = Uuid.parse(selectedDefinitionId!!),
                                startDateTime = parsedDateTime,
                                title = titleOverride.takeIf { it.isNotBlank() },
                                description = descriptionOverride.takeIf { it.isNotBlank() },
                                duration = durationHours.hours + durationMinutes.minutes,
                                price = priceOverride?.toDouble() ?: 0.0,
                                capacity = capacityOverride,
                                allowedPaymentTypes = allowedPayments,
                                customFields = emptyList(),
                            )

                            val dateTimes = if (isRecurring && previewDates.isNotEmpty()) previewDates else listOf(parsedDateTime)

                            if (dateTimes.isEmpty()) {
                                toastData = ToastData("Žádné termíny ke vytvoření.", ToastType.Error)
                                return@onClick
                            }

                            scope.launch {
                                var failed = false
                                for (dt in dateTimes) {
                                    authEventService.createEventInstance(baseRequest.copy(startDateTime = dt))
                                        .onLeft { error ->
                                            toastData = ToastData("Chyba při vytváření termínu $dt: $error", ToastType.Error)
                                            failed = true
                                        }
                                    if (failed) break
                                }
                                if (!failed) {
                                    toastData = ToastData(
                                        if (dateTimes.size > 1) "Vytvořeno ${dateTimes.size} termínů!" else "Termín byl úspěšně vypsán!",
                                        ToastType.Success
                                    )
                                    delay(500)
                                    router.navigate("/admin/events")
                                }
                            }
                        }
                        span(className = "icon-[heroicons--check] size-5")
                        +(if (isRecurring && previewDates.size > 1) "Vypsat ${previewDates.size} termínů" else "Vypsat termín")
                    }
                }
            }
        }
    }

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}