package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.components.CancellationPolicyBox
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.CustomFieldValidation
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.activeCustomFieldValidation
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.formatPriceHours
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.timeMultiplierHours
import cz.svitaninymburk.projects.reservations.ui.util.label
import cz.svitaninymburk.projects.reservations.user.User
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
    if (target == null) return

    val currentStrings by strings
    val scope = rememberCoroutineScope()
    val model = remember(target, user) {
        buildReservationFormModel(scope, target, user, initialWalletCode, asWaitlist)
    }

    LaunchedEffect(target) { model.lookUpWallet() }

    div(className = "modal modal-open modal-bottom sm:modal-middle bg-base-300/65 z-50") {
        div(className = "modal-box bg-base-100 shadow-xl border border-base-200 max-h-[92vh] overflow-hidden rounded-t-2xl sm:rounded-2xl flex flex-col p-0") {

            div(className = "overflow-y-auto flex-1 p-4 sm:p-6") {

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

                // DOČASNÉ: ukazatel, která varianta validace povinných polí běží.
                // Odstranit spolu s CustomFieldValidation, až se jedna vybere.
                val variant = activeCustomFieldValidation
                div(className = "alert py-2 text-sm mt-2 ${if (variant == CustomFieldValidation.PROPOSED) "alert-info" else "alert-warning"}") {
                    span(className = "icon-[heroicons--beaker] size-4")
                    span {
                        if (variant == CustomFieldValidation.PROPOSED) {
                            +"Validace: NÁVRH — prázdné povinné pole a nezaškrtnutý souhlas neprojdou, číslo se hlídá na min/max"
                        } else {
                            +"Validace: DNEŠNÍ STAV — vyplněné a vymazané povinné pole projde, povinný souhlas projde nezaškrtnutý"
                        }
                    }
                }

                // Cena info
                div(className = "py-4") {
                    div(className = "stats shadow w-full bg-base-200/50") {
                        div(className = "stat py-2") {
                            div(className = "stat-title") { +currentStrings.formTotalPrice }
                            div(className = "stat-value text-primary text-xl sm:text-2xl") {
                                if (model.total == 0.0) +currentStrings.free
                                else +"${model.total} ${currentStrings.currency}"
                            }
                            div(className = "stat-desc") {
                                // Bez volby počtu míst je "× 1 osob" jen šum — rozpad ceny ho vynechá.
                                val seatsPart =
                                    if (target.allowMultipleSeats) " × ${model.seats} ${currentStrings.persons}" else ""
                                val hours = timeMultiplierHours(target, model.customValues)
                                if (hours != null) {
                                    +"${target.price} ${currentStrings.currency}$seatsPart × ${formatPriceHours(hours)} ${currentStrings.hours}"
                                } else {
                                    +"${target.price} ${currentStrings.currency}$seatsPart"
                                }
                            }
                        }
                    }

                    if (model.deduction > 0.0) {
                        div(className = "mt-2 bg-success/10 border border-success/30 rounded-xl px-4 py-3 flex flex-col gap-1 text-sm") {
                            div(className = "flex justify-between") {
                                span(className = "text-base-content/60") { +currentStrings.walletCreditApplied }
                                span(className = "font-medium text-success") { +"− ${model.deduction.toInt()} ${currentStrings.currency}" }
                            }
                            div(className = "flex justify-between font-semibold") {
                                span { +currentStrings.remainingToPay }
                                span(className = "text-primary") {
                                    if (model.remaining == 0.0) +currentStrings.free
                                    else +"${model.remaining.toInt()} ${currentStrings.currency}"
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
                            text(value = model.firstName, className = "input input-bordered input-lg sm:input-md w-full", name = "given-name") {
                                id("reservation-name")
                                autocomplete(Autocomplete.GivenName)
                                placeholder(currentStrings.nameHint)
                                attribute("aria-required", "true")
                                onInput { model.setFirstName(value ?: "") }
                            }
                        }
                        label(className = "form-control w-full") {
                            div(className = "label") {
                                span(className = "label-text") { +currentStrings.surnameLabel }
                                span(className = "text-error") { +"*" }
                            }
                            text(value = model.lastName, className = "input input-bordered input-lg sm:input-md w-full", name = "family-name") {
                                id("reservation-surname")
                                autocomplete(Autocomplete.FamilyName)
                                placeholder(currentStrings.surnameHint)
                                attribute("aria-required", "true")
                                onInput { model.setLastName(value ?: "") }
                            }
                        }
                    }

                    // 2. Email
                    label(className = "form-control w-full") {
                        div(className = "label") {
                            span(className = "label-text") { +currentStrings.emailLabel }
                            span(className = "text-error") { +"*" }
                        }
                        text(value = model.email, type = InputType.Email, className = "input input-bordered input-lg sm:input-md w-full", name = "email") {
                            id("reservation-email")
                            autocomplete(Autocomplete.Email)
                            placeholder(currentStrings.emailHint)
                            attribute("aria-required", "true")
                            onInput { model.setEmail(value ?: "") }
                        }
                    }

                    // 3. Telefon
                    label(className = "form-control w-full") {
                        div(className = "label") {
                            span(className = "label-text") { +currentStrings.phoneLabel }
                            span(className = "text-error") { +"*" }
                        }
                        text(value = model.phone, type = InputType.Tel, className = "input input-bordered input-lg sm:input-md w-full", name = "tel") {
                            id("reservation-phone")
                            autocomplete(Autocomplete.Tel)
                            placeholder(currentStrings.phoneHint)
                            attribute("aria-required", "true")
                            onInput { model.setPhone(value ?: "") }
                            onEvent<Event>("blur") { model.normalizePhone() }
                        }
                        div(className = "label") {
                            span(className = "label-text-alt text-base-content/60") { +currentStrings.phoneHintAlt }
                        }
                    }

                    if (target.customFields.isNotEmpty()) {
                        div(className = "divider text-xs text-base-content/50 my-1") { +currentStrings.moreDetails }

                        target.customFields.forEach { field ->
                            renderCustomField(field, model.customValues, target)
                        }
                    }

                    // 4. Počet míst a Platba
                    div(className = "grid grid-cols-1 sm:grid-cols-3 gap-3") {

                        // Počet míst — u akcí bez volby počtu míst se pole nezobrazuje a rezervuje se 1 místo
                        if (target.allowMultipleSeats) {
                            label(className = "form-control w-full sm:col-span-1") {
                                div(className = "label") {
                                    span(className = "label-text") { +currentStrings.seatCountLabel }
                                    span(className = "text-error") { +"*" }
                                }
                                text(value = model.seats.toString(), type = InputType.Number, className = "input input-bordered input-lg sm:input-md w-full") {
                                    onInput { model.setSeats(this.value?.toIntOrNull()) }
                                    onChange { model.setSeats(this.value?.toIntOrNull()) }
                                }
                                if (model.seatsExceeded) {
                                    p(className = "text-warning text-sm mt-1") {
                                        +currentStrings.seatCountMaxReached(target.maxCapacity)
                                    }
                                }
                            }
                        }

                        // Typ platby — skryto pokud peněženka pokrývá celou cenu
                        if (model.showsPaymentPicker) {
                            // Bez pole s počtem míst zabere platba celou šířku mřížky.
                            val paymentSpan = if (target.allowMultipleSeats) "sm:col-span-2" else "sm:col-span-3"
                            label(className = "form-control w-full $paymentSpan") {
                                div(className = "label") {
                                    span(className = "label-text") { +currentStrings.paymentType }
                                    span(className = "text-error") { +"*" }
                                }
                                select(className = "select select-bordered select-lg sm:select-md w-full") {
                                    for (paymentOption in target.allowedPaymentTypes) {
                                        option(paymentOption.name, label = paymentOption.label)
                                    }

                                    onChange { event ->
                                        val selectedValue = (event.target as? HTMLSelectElement)?.value
                                        model.setPaymentType(
                                            PaymentInfo.Type.valueOf(selectedValue ?: PaymentInfo.Type.BANK_TRANSFER.name)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Slevový kód peněženky
                    label(className = "flex items-center gap-2 cursor-pointer w-fit") {
                        checkBox(value = model.walletExpanded, className = "checkbox checkbox-sm") {
                            onChange { model.setWalletExpanded(value) }
                        }
                        span(className = "label-text") { +currentStrings.walletHasCode }
                    }
                    if (model.walletExpanded) {
                        label(className = "form-control w-full") {
                            div(className = "label") {
                                span(className = "label-text") { +currentStrings.walletCode }
                            }
                            text(value = model.walletCode, className = "input input-bordered input-lg sm:input-md w-full") {
                                placeholder(currentStrings.walletCodePlaceholder)
                                onInput { model.setWalletCode(value ?: "") }
                            }
                            div(className = "label") {
                                span(className = "label-text-alt text-base-content/50") { +currentStrings.walletCodeHint }
                            }
                            model.walletInfo?.let { info ->
                                div(className = "label pt-0") {
                                    span(className = "label-text-alt text-success font-medium") {
                                        +"${currentStrings.walletBalance}: ${info.balance.toInt()} ${currentStrings.currency}"
                                    }
                                }
                                if (!info.emailMatches) {
                                    div(className = "alert alert-warning py-2 text-sm mt-1") {
                                        span(className = "icon-[heroicons--exclamation-triangle] size-4")
                                        span { +currentStrings.walletEmailMismatchWarning }
                                    }
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

            } // end scrollable content

            // --- STICKY FOOTER ---
            div(className = "flex-shrink-0 border-t border-base-200 bg-base-100 px-4 sm:px-6 py-3 flex items-center gap-3") {
                div(className = "flex-1 min-w-0") {
                    div(className = "text-xs text-base-content/60") {
                        if (model.deduction > 0.0) +currentStrings.remainingToPay else +currentStrings.formTotalPrice
                    }
                    div(className = "font-semibold text-primary") {
                        if (model.remaining == 0.0) +currentStrings.free
                        else +"${model.remaining.toInt()} ${currentStrings.currency}"
                    }
                }
                button(className = "btn btn-ghost min-h-10 h-10") {
                    disabled(isSubmitting)
                    onClick { if (!isSubmitting) onClose() }
                    +currentStrings.cancel
                }
                button(className = "btn btn-primary px-6 min-h-10 h-10") {
                    disabled(!model.isValid || isSubmitting)
                    onClick {
                        if (!isSubmitting) onSubmit(target, model.formData(currentStrings.locale))
                    }
                    if (isSubmitting) {
                        span(className = "loading loading-spinner loading-sm")
                    } else if (model.isValid) {
                        span(className = "icon-[heroicons--check] size-5")
                    }
                    +currentStrings.reserve
                }
            }
        }

        // Backdrop musí být během odesílání zamčený stejně jako Zrušit/Zavřít —
        // jinak klik mimo modal odmountuje spinner, request běží dál neviditelně
        // a uživatel si myslí, že se nic nestalo.
        div(className = "modal-backdrop") {
            onClick { if (!isSubmitting) onClose() }
            button {
                disabled(isSubmitting)
                +currentStrings.close
            }
        }
    }
}
