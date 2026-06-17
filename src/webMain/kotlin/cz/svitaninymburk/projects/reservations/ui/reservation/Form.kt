package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.PriceModifier
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import cz.svitaninymburk.projects.reservations.event.calculateTotalPrice
import cz.svitaninymburk.projects.reservations.event.hoursFromRange
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.util.PhoneNumber
import cz.svitaninymburk.projects.reservations.ui.components.CancellationPolicyBox
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.label
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.form
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.label
import dev.kilua.html.option
import dev.kilua.html.p
import dev.kilua.html.span
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import web.events.Event
import web.html.HTMLSelectElement

@Composable
fun IComponent.ReservationModal(
    target: ReservationTarget?,
    user: User?,
    initialWalletCode: String? = null,
    isSubmitting: Boolean = false,
    asWaitlist: Boolean = false,
    onClose: () -> Unit,
    onSubmit: (ReservationTarget, ReservationFormData) -> Unit
) {
    val currentStrings by strings
    val scope = rememberCoroutineScope()
    val reservationService = getService<ReservationServiceInterface>(RpcSerializersModules)

    // Stavy formuláře — prefill z přihlášeného uživatele (User model nemá telefon)
    var firstName by remember(target, user) { mutableStateOf(user?.name.orEmpty()) }
    var lastName by remember(target, user) { mutableStateOf(user?.surname.orEmpty()) }
    var email by remember(target, user) { mutableStateOf(user?.email.orEmpty()) }
    var phone by remember(target) { mutableStateOf("") }
    var seats by remember(target) { mutableStateOf(1) }
    var seatsExceeded by remember(target) { mutableStateOf(false) }
    var paymentType by remember(target) { mutableStateOf(PaymentInfo.Type.BANK_TRANSFER) }
    val customValuesState = remember(target) { mutableStateMapOf<String, CustomFieldValue>() }
    var walletCode by remember(target) { mutableStateOf(initialWalletCode ?: "") }
    var walletInfo by remember(target) { mutableStateOf<WalletInfo?>(null) }
    var walletExpanded by remember(target) { mutableStateOf(initialWalletCode != null) }

    LaunchedEffect(target) {
        if (walletCode.length == 14 && email.isNotBlank()) {
            walletInfo = reservationService.getWalletInfo(walletCode, email).getOrNull()
        }
    }

    val isValid =
        firstName.isNotBlank() &&
        lastName.isNotBlank() &&
        email.contains("@") &&
        PhoneNumber.isValid(phone) &&
        seats > 0 &&
        (target?.customFields?.all { field ->
            (if (!field.isRequired) true
            else {
                val value = customValuesState[field.key]
                when (field) {
                    is BooleanFieldDefinition -> true
                    is TextFieldDefinition, is NumberFieldDefinition -> value != null && value.toString().isNotBlank()
                    is TimeRangeFieldDefinition -> {
                        val range = (value as? TimeRangeValue) ?: return@all false
                        range.from < range.to
                                && range.from in target.startDateTime.time..target.endDateTime.time
                                && range.to in target.startDateTime.time..target.endDateTime.time
                    }
                }
            })
        } ?: true)

    if (target != null) {
        div(className = "modal modal-open modal-bottom sm:modal-middle bg-base-300/65 z-50") {
            div(className = "modal-box bg-base-100 shadow-xl border border-base-200 max-h-[92vh] overflow-y-auto rounded-t-2xl sm:rounded-2xl p-4 sm:p-6") {

                // --- HLAVIČKA ---
                h3(className = "font-bold text-lg flex items-center gap-2") {
                    span(className = "icon-[heroicons--ticket] size-6 text-primary")
                    if (asWaitlist) +currentStrings.substituteFormHeading
                    else +currentStrings.reservationFor(target.title)
                }
                if (asWaitlist) {
                    p(className = "text-sm text-base-content/70 mt-1 mb-2") {
                        +currentStrings.substituteInfoNote
                    }
                }

                // Cena info
                div(className = "py-4") {
                    val total = calculateTotalPrice(
                        basePrice = target.price,
                        seatCount = seats,
                        customFields = target.customFields,
                        customValues = customValuesState,
                    )
                    val walletDeduction = walletInfo?.let { minOf(it.balance, total) } ?: 0.0

                    div(className = "stats shadow w-full bg-base-200/50") {
                        div(className = "stat py-2") {
                            div(className = "stat-title") { +currentStrings.formTotalPrice }
                            div(className = "stat-value text-primary text-xl sm:text-2xl") {
                                if (total == 0.0) +currentStrings.free else +"$total ${currentStrings.currency}"
                            }
                            div(className = "stat-desc") {
                                val timeField = target.customFields
                                    .filterIsInstance<TimeRangeFieldDefinition>()
                                    .firstOrNull { it.priceModifier is PriceModifier.TimeMultiplier }
                                val hours = timeField?.let { f ->
                                    (customValuesState[f.key] as? TimeRangeValue)
                                        ?.takeIf { it.to > it.from }
                                        ?.let { v -> hoursFromRange(v) }
                                }
                                if (hours != null) {
                                    val rounded = kotlin.math.round(hours * 10) / 10.0
                                    val displayHours = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
                                    +"${target.price} ${currentStrings.currency} × $seats ${currentStrings.persons} × $displayHours ${currentStrings.hours}"
                                } else {
                                    +"${target.price} ${currentStrings.currency} × $seats ${currentStrings.persons}"
                                }
                            }
                        }
                    }

                    if (walletDeduction > 0.0) {
                        val afterWallet = total - walletDeduction
                        div(className = "mt-2 bg-success/10 border border-success/30 rounded-xl px-4 py-3 flex flex-col gap-1 text-sm") {
                            div(className = "flex justify-between") {
                                span(className = "text-base-content/60") { +currentStrings.walletCreditApplied }
                                span(className = "font-medium text-success") { +"− ${walletDeduction.toInt()} ${currentStrings.currency}" }
                            }
                            div(className = "flex justify-between font-semibold") {
                                span { +currentStrings.remainingToPay }
                                span(className = "text-primary") {
                                    if (afterWallet == 0.0) +currentStrings.free
                                    else +"${afterWallet.toInt()} ${currentStrings.currency}"
                                }
                            }
                        }
                    }
                }

                // --- FORMULÁŘ ---
                form(className = "flex flex-col gap-3") {
                    onEvent<Event>("submit") { it.preventDefault() }

                    // 1. Jméno a Příjmení (Vedle sebe)
                    div(className = "flex flex-col sm:flex-row gap-3") {
                        label(className = "form-control w-full") {
                            div(className = "label") {
                                span(className = "label-text") { +currentStrings.nameLabel }
                                span(className = "text-error") { +"*" }
                            }
                            text(value = firstName, className = "input input-bordered input-lg sm:input-md w-full", name = "given-name") {
                                id("reservation-name")
                                autocomplete(Autocomplete.GivenName)
                                placeholder(currentStrings.nameHint)
                                attribute("aria-required", "true")
                                onInput { firstName = value ?: "" }
                            }
                        }
                        label(className = "form-control w-full") {
                            div(className = "label") {
                                span(className = "label-text") { +currentStrings.surnameLabel }
                                span(className = "text-error") { +"*" }
                            }
                            text(value = lastName, className = "input input-bordered input-lg sm:input-md w-full", name = "family-name") {
                                id("reservation-surname")
                                autocomplete(Autocomplete.FamilyName)
                                placeholder(currentStrings.surnameHint)
                                attribute("aria-required", "true")
                                onInput { lastName = value ?: "" }
                            }
                        }
                    }

                    // 2. Email
                    label(className = "form-control w-full") {
                        div(className = "label") {
                            span(className = "label-text") { +currentStrings.emailLabel }
                            span(className = "text-error") { +"*" }
                        }
                        text(value = email, type = InputType.Email, className = "input input-bordered input-lg sm:input-md w-full", name = "email") {
                            id("reservation-email")
                            autocomplete(Autocomplete.Email)
                            placeholder(currentStrings.emailHint)
                            attribute("aria-required", "true")
                            onInput {
                                email = value ?: ""
                                if (walletCode.length == 14) {
                                    scope.launch {
                                        walletInfo = reservationService.getWalletInfo(walletCode, email).getOrNull()
                                    }
                                }
                            }
                        }
                    }

                    // 3. Telefon
                    label(className = "form-control w-full") {
                        div(className = "label") {
                            span(className = "label-text") { +currentStrings.phoneLabel }
                            span(className = "text-error") { +"*" }
                        }
                        text(value = phone, type = InputType.Tel, className = "input input-bordered input-lg sm:input-md w-full", name = "tel") {
                            id("reservation-phone")
                            autocomplete(Autocomplete.Tel)
                            placeholder(currentStrings.phoneHint)
                            attribute("aria-required", "true")
                            onInput { phone = value ?: "" }
                            onEvent<Event>("blur") {
                                if (phone.isNotBlank()) phone = PhoneNumber.format(phone)
                            }
                        }
                        div(className = "label") {
                            span(className = "label-text-alt text-base-content/60") { +currentStrings.phoneHintAlt }
                        }
                    }

                    if (target.customFields.isNotEmpty()) {
                        div(className = "divider text-xs text-base-content/50 my-1") { +currentStrings.moreDetails }

                        target.customFields.forEach { field ->
                            renderCustomField(field, customValuesState, target)
                        }
                    }

                    // 4. Počet míst a Platba
                    div(className = "grid grid-cols-1 sm:grid-cols-3 gap-3") {
                        val totalForPayment = calculateTotalPrice(
                            basePrice = target.price,
                            seatCount = seats,
                            customFields = target.customFields,
                            customValues = customValuesState,
                        )
                        val afterWallet = totalForPayment - (walletInfo?.let { minOf(it.balance, totalForPayment) } ?: 0.0)

                        // Počet míst
                        label(className = "form-control w-full sm:col-span-1") {
                            div(className = "label") {
                                span(className = "label-text") { +currentStrings.seatCountLabel }
                                span(className = "text-error") { +"*" }
                            }
                            text(value = seats.toString(), type = InputType.Number, className = "input input-bordered input-lg sm:input-md w-full") {
                                onInput {
                                    val typed = it.data?.toInt() ?: 1
                                    seats = typed.coerceIn(1, target.maxCapacity)
                                    seatsExceeded = typed > target.maxCapacity
                                }
                            }
                            if (seatsExceeded) {
                                p(className = "text-warning text-sm mt-1") {
                                    +currentStrings.seatCountMaxReached(target.maxCapacity)
                                }
                            }
                        }

                        // Typ platby — skryto pokud peněženka pokrývá celou cenu
                        if (afterWallet > 0.0) {
                            label(className = "form-control w-full sm:col-span-2") {
                                div(className = "label") {
                                    span(className = "label-text") { +currentStrings.paymentType }
                                    span(className = "text-error") { +"*" }
                                }
                                select(className = "select select-bordered select-lg sm:select-md w-full") {
                                    for (paymentOption in target.allowedPaymentTypes) {
                                        option(paymentOption.name, label = paymentOption.label)
                                    }

                                    onChange { event ->
                                        val targetSelect = event.target as? HTMLSelectElement
                                        val selectedValue = targetSelect?.value
                                        paymentType = PaymentInfo.Type.valueOf(selectedValue ?: PaymentInfo.Type.BANK_TRANSFER.name)
                                    }
                                }
                            }
                        }
                    }

                    // Slevový kód peněženky
                    label(className = "flex items-center gap-2 cursor-pointer w-fit") {
                        checkBox(value = walletExpanded, className = "checkbox checkbox-sm") {
                            onChange {
                                walletExpanded = value
                                if (!walletExpanded) {
                                    walletCode = ""
                                    walletInfo = null
                                }
                            }
                        }
                        span(className = "label-text") { +currentStrings.walletHasCode }
                    }
                    if (walletExpanded) {
                        label(className = "form-control w-full") {
                            div(className = "label") {
                                span(className = "label-text") { +currentStrings.walletCode }
                            }
                            text(value = walletCode, className = "input input-bordered input-lg sm:input-md w-full") {
                                placeholder(currentStrings.walletCodePlaceholder)
                                onInput {
                                    walletCode = value ?: ""
                                    if (walletCode.length == 14) {
                                        scope.launch {
                                            walletInfo = reservationService.getWalletInfo(walletCode, email).getOrNull()
                                        }
                                    } else {
                                        walletInfo = null
                                    }
                                }
                            }
                            div(className = "label") {
                                span(className = "label-text-alt text-base-content/50") { +currentStrings.walletCodeHint }
                            }
                            if (walletInfo != null) {
                                div(className = "label pt-0") {
                                    span(className = "label-text-alt text-success font-medium") {
                                        +"${currentStrings.walletBalance}: ${walletInfo!!.balance.toInt()} ${currentStrings.currency}"
                                    }
                                }
                            }
                            if (walletInfo != null && !walletInfo!!.emailMatches) {
                                div(className = "alert alert-warning py-2 text-sm mt-1") {
                                    span(className = "icon-[heroicons--exclamation-triangle] size-4")
                                    span { +currentStrings.walletEmailMismatchWarning }
                                }
                            }
                        }
                    }
                }

                div(className = "mt-1") {
                    CancellationPolicyBox()
                }

                p(className = "text-xs text-base-content/50 mt-1") {
                    +currentStrings.requiredFieldLegend
                }

                // --- AKCE (Footer) ---
                div(className = "modal-action flex-col-reverse sm:flex-row gap-2") {
                    button(className = "btn btn-ghost w-full sm:w-auto min-h-11") {
                        disabled(isSubmitting)
                        onClick { if (!isSubmitting) onClose() }
                        +currentStrings.cancel
                    }
                    button(className = "btn btn-primary px-8 w-full sm:w-auto min-h-11") {
                        disabled(!isValid || isSubmitting)
                        onClick {
                            if (!isSubmitting) {
                                val t = calculateTotalPrice(target.price, seats, target.customFields, customValuesState)
                                val wd = walletInfo?.let { minOf(it.balance, t) } ?: 0.0
                                val effectivePaymentType = if (wd == t) PaymentInfo.Type.FREE else paymentType
                                onSubmit(
                                    target,
                                    ReservationFormData(firstName, lastName, email, phone, if (asWaitlist) 1 else seats, effectivePaymentType, customValuesState, currentStrings.locale, walletCode.ifBlank { null }, asWaitlist)
                                )
                            }
                        }
                        if (isSubmitting) {
                            span(className = "loading loading-spinner loading-sm")
                        } else if (isValid) {
                            span(className = "icon-[heroicons--check] size-5")
                        }
                        +currentStrings.reserve
                    }
                }
            }

            div(className = "modal-backdrop") {
                onClick { onClose() }
                button { +currentStrings.close }
            }
        }
    }
}