package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.label
import dev.kilua.html.option
import dev.kilua.html.span
import web.html.HTMLSelectElement

@Composable
fun IComponent.ReservationModal(
    target: ReservationTarget?,
    onClose: () -> Unit,
    onSubmit: (ReservationTarget, ReservationFormData) -> Unit
) {
    val currentStrings by strings

    // Stavy formuláře
    var firstName by remember(target) { mutableStateOf("") }
    var lastName by remember(target) { mutableStateOf("") }
    var email by remember(target) { mutableStateOf("") }
    var phone by remember(target) { mutableStateOf("") }
    var seats by remember(target) { mutableStateOf(1) }
    var paymentType by remember(target) { mutableStateOf(PaymentInfo.Type.BANK_TRANSFER) }
    val customValuesState = remember(target) { mutableStateMapOf<String, CustomFieldValue>() }

    val isValid =
        firstName.isNotBlank() &&
        lastName.isNotBlank() &&
        email.contains("@") &&
        phone.length >= 9 &&
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
        div(className = "modal modal-open modal-bottom sm:modal-middle bg-base-300/50 backdrop-blur-sm z-50") {
            div(className = "modal-box bg-base-100 shadow-xl border border-base-200") {

                // --- HLAVIČKA ---
                h3(className = "font-bold text-lg flex items-center gap-2") {
                    span(className = "icon-[heroicons--ticket] size-6 text-primary")
                    +currentStrings.reservationFor(target.title)
                }

                // Cena info
                div(className = "py-4") {
                    div(className = "stats shadow w-full bg-base-200/50") {
                        div(className = "stat py-2") {
                            div(className = "stat-title") { +currentStrings.formTotalPrice }
                            div(className = "stat-value text-primary text-2xl") {
                                // Dynamický výpočet ceny
                                val total = target.price * seats
                                if (total == 0.0) +currentStrings.free else +"$total ${currentStrings.currency}"
                            }
                            div(className = "stat-desc") {
                                +"${target.price} ${currentStrings.currency} x $seats ${currentStrings.persons}"
                            }
                        }
                    }
                }

                // --- FORMULÁŘ ---
                div(className = "flex flex-col gap-3") {

                    // 1. Jméno a Příjmení (Vedle sebe)
                    div(className = "flex gap-3") {
                        label(className = "form-control w-full") {
                            div(className = "label") { span(className = "label-text") { +currentStrings.nameLabel } }
                            text(value = firstName, className = "input input-bordered w-full") {
                                placeholder(currentStrings.nameHint)
                                onInput { firstName = value ?: "" }
                            }
                        }
                        label(className = "form-control w-full") {
                            div(className = "label") { span(className = "label-text") { +currentStrings.surnameLabel } }
                            text(value = lastName, className = "input input-bordered w-full") {
                                placeholder(currentStrings.surnameHint)
                                onInput { lastName = value ?: "" }
                            }
                        }
                    }

                    // 2. Email
                    label(className = "form-control w-full") {
                        div(className = "label") { span(className = "label-text") { +currentStrings.emailLabel } }
                        text(value = email, type = InputType.Email, className = "input input-bordered w-full") {
                            placeholder(currentStrings.emailHint)
                            onInput { email = value ?: "" }
                        }
                    }

                    // 3. Telefon
                    label(className = "form-control w-full") {
                        div(className = "label") { span(className = "label-text") { +currentStrings.phoneLabel } }
                        text(value = phone, type = InputType.Tel, className = "input input-bordered w-full") {
                            placeholder(currentStrings.phoneHint)
                            onInput { phone = value ?: "" }
                        }
                    }

                    if (target.customFields.isNotEmpty()) {
                        div(className = "divider text-xs text-base-content/50 my-1") { +currentStrings.moreDetails }

                        target.customFields.forEach { field ->
                            renderCustomField(field, customValuesState)
                        }
                    }

                    // 4. Počet míst a Platba
                    div(className = "flex gap-3") {
                        // Počet míst
                        label(className = "form-control w-1/3") {
                            div(className = "label") { span(className = "label-text") { +currentStrings.seatCountLabel } }
                            text(value = seats.toString(), type = InputType.Number, className = "input input-bordered w-full") {
                                onInput { seats = it.data?.toInt()?.coerceIn(1, target.maxCapacity) ?: 1 }
                            }
                        }

                        // Typ platby
                        label(className = "form-control w-2/3") {
                            div(className = "label") { span(className = "label-text") { +currentStrings.paymentType } }
                            select(className = "select select-bordered w-full") {
                                option(value = PaymentInfo.Type.BANK_TRANSFER.name, label = currentStrings.bankTransfer)
                                option(value = PaymentInfo.Type.ON_SITE.name, label = currentStrings.onSite)

                                onChange { event ->
                                    val targetSelect = event.target as? HTMLSelectElement
                                    val selectedValue = targetSelect?.value
                                    paymentType = PaymentInfo.Type.valueOf(selectedValue ?: PaymentInfo.Type.BANK_TRANSFER.name)
                                }
                            }
                        }
                    }
                }

                // --- AKCE (Footer) ---
                div(className = "modal-action") {
                    // Cancel
                    button(className = "btn btn-ghost") {
                        onClick { onClose() }
                        +currentStrings.cancel
                    }
                    // Submit
                    button(className = "btn btn-primary px-8") {
                        disabled(!isValid)
                        onClick {
                            onSubmit(
                                target,
                                ReservationFormData(firstName, lastName, email, phone, seats, paymentType, customValuesState)
                            )
                        }
                        if (isValid) {
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