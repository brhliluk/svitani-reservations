package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import web.html.HTMLSelectElement
import kotlin.js.js
import kotlin.uuid.Uuid

@Composable
fun IComponent.AdminCreateEventSeriesScreen(preselectedDefinitionId: String? = null) {
    val router = Router.current
    val eventService = getService<EventServiceInterface>(RpcSerializersModules)
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)

    val scope = rememberCoroutineScope()
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    var definitions by remember { mutableStateOf<List<EventDefinition>>(emptyList()) }
    var isLoadingDefinitions by remember { mutableStateOf(true) }

    var selectedDefinitionId by remember { mutableStateOf(preselectedDefinitionId) }

    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var lessonCount by remember { mutableIntStateOf(1) }

    var titleOverride by remember { mutableStateOf("") }
    var descriptionOverride by remember { mutableStateOf("") }
    var priceOverride by remember { mutableStateOf<Number?>(0) }
    var capacityOverride by remember { mutableIntStateOf(10) }
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }

    fun applyDefinitionDefaults(definition: EventDefinition) {
        titleOverride = definition.title
        descriptionOverride = definition.description
        priceOverride = definition.defaultPrice
        capacityOverride = definition.defaultCapacity
        allowBankTransfer = definition.allowedPaymentTypes.contains(PaymentInfo.Type.BANK_TRANSFER)
        allowOnSite = definition.allowedPaymentTypes.contains(PaymentInfo.Type.ON_SITE)
    }

    val selectedDefinition = definitions.find { it.id.toString() == selectedDefinitionId }
    val isRecurring = selectedDefinition?.recurrenceType != null
        && selectedDefinition.recurrenceType != RecurrenceType.NONE
        && selectedDefinition.recurrenceEndDate != null

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

    LaunchedEffect(selectedDefinitionId, startDate) {
        val def = selectedDefinition ?: return@LaunchedEffect
        if (!isRecurring || startDate.isBlank()) return@LaunchedEffect
        val parsedStart = try { LocalDate.parse(startDate) } catch (_: Exception) { return@LaunchedEffect }
        val autoFill = computeSeriesAutoFill(parsedStart, def.recurrenceType, def.recurrenceEndDate!!)
            ?: return@LaunchedEffect
        endDate = autoFill.endDate.toString()
        lessonCount = autoFill.lessonCount
    }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

        // Hlavička
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { js("window.history.back()") }
            }
            div {
                h1(className = "text-3xl font-bold text-base-content") { +"Vytvořit kurz" }
                p(className = "text-base-content/60") { +"Nastavte období a počet lekcí. Detaily jsou předvyplněné ze šablony." }
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
                    div(className = "text-sm") { +"Než vytvoříte kurz, musíte nejprve vytvořit šablonu události." }
                }
                button(className = "btn btn-sm btn-primary") {
                    onClick { router.navigate("/admin/events/create/definition") }
                    +"Vytvořit šablonu"
                }
            }
        } else {
            // --- VÝBĚR ŠABLONY A OBDOBÍ ---
            div(className = "card bg-base-100 shadow-sm border-t-4 border-secondary") {
                div(className = "card-body") {
                    div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                        // Výběr šablony (skryto pokud je předvybrána)
                        if (preselectedDefinitionId == null) {
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-bold") { +"Ze které šablony vycházíme?" } }
                                select(className = "select select-bordered w-full text-base") {
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

                        // Datum začátku
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +"Datum začátku" } }
                            text(value = startDate, type = InputType.Date, className = "input input-bordered w-full") {
                                onInput { startDate = value ?: "" }
                            }
                        }

                        // Datum konce
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +"Datum konce" } }
                            text(value = endDate, type = InputType.Date, className = "input input-bordered w-full") {
                                onInput { endDate = value ?: "" }
                            }
                        }

                        // Počet lekcí
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +"Počet lekcí" } }
                            div(className = "relative flex items-center") {
                                numeric(value = lessonCount, min = 1, decimals = 0, className = "input input-bordered w-full pr-16") {
                                    attribute("step", "1")
                                    onInput { lessonCount = value?.toInt() ?: 1 }
                                }
                                span(className = "absolute right-4 text-base-content/50 text-sm") { +"lekcí" }
                            }
                        }

                        if (isRecurring && startDate.isNotBlank()) {
                            div(className = "md:col-span-2") {
                                div(className = "alert alert-info py-2 text-sm") {
                                    span(className = "icon-[heroicons--information-circle] size-4")
                                    +"Datum konce a počet lekcí byly předvyplněny ze šablony. Můžete je upravit."
                                }
                            }
                        }
                    }
                }
            }

            // --- ÚPRAVY PRO TENTO KURZ ---
            if (selectedDefinitionId != null) {
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-2") { +"Úpravy pro tento kurz" }
                        p(className = "text-sm text-base-content/60 mb-4") {
                            +"Předvyplněno ze šablony. Změny se projeví pouze u tohoto kurzu."
                        }

                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +"Název kurzu" } }
                                text(value = titleOverride, className = "input input-bordered w-full") {
                                    onInput { titleOverride = value ?: "" }
                                }
                            }

                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +"Popis" } }
                                textArea(value = descriptionOverride, className = "textarea textarea-bordered h-24 w-full") {
                                    onInput { descriptionOverride = value ?: "" }
                                }
                            }

                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +"Cena (celý kurz)" } }
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

                // --- ULOŽIT ---
                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") {
                        onClick { js("window.history.back()") }
                        +"Zrušit"
                    }
                    button(className = "btn btn-secondary") {
                        onClick {
                            if (startDate.isBlank() || endDate.isBlank()) {
                                toastData = ToastData("Musíte vyplnit datum začátku a konce.", ToastType.Error)
                                return@onClick
                            }
                            if (titleOverride.isBlank()) {
                                toastData = ToastData("Název kurzu je povinný.", ToastType.Error)
                                return@onClick
                            }

                            val parsedStartDate = try { LocalDate.parse(startDate) } catch (e: Exception) {
                                toastData = ToastData("Neplatný formát data začátku.", ToastType.Error)
                                return@onClick
                            }
                            val parsedEndDate = try { LocalDate.parse(endDate) } catch (e: Exception) {
                                toastData = ToastData("Neplatný formát data konce.", ToastType.Error)
                                return@onClick
                            }
                            if (parsedEndDate < parsedStartDate) {
                                toastData = ToastData("Datum konce musí být po datu začátku.", ToastType.Error)
                                return@onClick
                            }

                            val allowedPayments = mutableListOf<PaymentInfo.Type>()
                            if (allowBankTransfer) allowedPayments.add(PaymentInfo.Type.BANK_TRANSFER)
                            if (allowOnSite) allowedPayments.add(PaymentInfo.Type.ON_SITE)

                            val request = CreateEventSeriesRequest(
                                definitionId = Uuid.parse(selectedDefinitionId!!),
                                title = titleOverride,
                                description = descriptionOverride,
                                price = priceOverride?.toDouble() ?: 0.0,
                                capacity = capacityOverride,
                                startDate = parsedStartDate,
                                endDate = parsedEndDate,
                                lessonCount = lessonCount,
                                allowedPaymentTypes = allowedPayments,
                            )

                            scope.launch {
                                adminService.createEventSeries(request)
                                    .onRight {
                                        toastData = ToastData("Kurz byl úspěšně vytvořen!", ToastType.Success)
                                        delay(500)
                                        router.navigate("/admin/events")
                                    }
                                    .onLeft { error ->
                                        toastData = ToastData("Chyba: $error", ToastType.Error)
                                    }
                            }
                        }
                        span(className = "icon-[heroicons--check] size-5")
                        +"Vytvořit kurz"
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
