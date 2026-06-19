package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.number.numeric
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.html.*
import web.html.HTMLSelectElement

@Composable
fun IComponent.OwnerEmailsField(
    emails: List<String>,
    onChange: (List<String>) -> Unit,
) {
    val currentStrings by strings
    div(className = "form-control w-full md:col-span-2") {
        label(className = "label") {
            span(className = "label-text font-medium") { +currentStrings.ownerEmailsLabel }
        }
        div(className = "flex flex-col gap-2") {
            emails.forEachIndexed { index, email ->
                div(className = "flex gap-2 items-center") {
                    text(value = email, className = "input input-bordered flex-1") {
                        placeholder(currentStrings.ownerEmailPlaceholder)
                        onInput {
                            onChange(emails.toMutableList().apply { set(index, value ?: "") })
                        }
                    }
                    if (emails.size > 1) {
                        button(className = "btn btn-ghost btn-sm btn-circle text-error") {
                            onClick {
                                onChange(emails.toMutableList().apply { removeAt(index) })
                            }
                            span(className = "icon-[heroicons--x-mark] size-4")
                        }
                    }
                }
            }
            button(className = "btn btn-outline btn-sm gap-2 self-start mt-1") {
                onClick { onChange(emails + "") }
                span(className = "icon-[heroicons--plus] size-4")
                +currentStrings.addOwnerEmailButton
            }
        }
    }
}

@Composable
fun IComponent.PriceCurrencyField(
    label: String,
    value: Number?,
    onChange: (Number?) -> Unit,
) {
    val currentStrings by strings
    div(className = "form-control w-full") {
        label(className = "label") { span(className = "label-text font-medium") { +label } }
        div(className = "relative flex items-center") {
            numeric(value = value, min = 0, className = "input input-bordered w-full pr-12") {
                onInput { onChange(this.value) }
                onChange { onChange(this.value) }
            }
            span(className = "absolute right-4 text-base-content/50 font-medium") { +currentStrings.currency }
        }
    }
}

@Composable
fun IComponent.CapacityField(
    value: Int,
    onChange: (Int) -> Unit,
) {
    val currentStrings by strings
    div(className = "form-control w-full") {
        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.capacityPersonLabel } }
        div(className = "relative flex items-center") {
            numeric(value = value, min = 1, decimals = 0, className = "input input-bordered w-full pr-12") {
                attribute("step", "1")
                onInput { onChange(this.value?.toInt() ?: 1) }
                onChange { onChange(this.value?.toInt() ?: 1) }
            }
            span(className = "absolute right-4 text-base-content/50") {
                span(className = "icon-[heroicons--users] size-5")
            }
        }
    }
}

@Composable
fun IComponent.WaitlistCapacityField(
    value: Int,
    onChange: (Int) -> Unit,
) {
    val currentStrings by strings
    div(className = "form-control w-full") {
        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.waitlistCapacityLabel } }
        div(className = "relative flex items-center") {
            numeric(value = value, min = 0, decimals = 0, className = "input input-bordered w-full pr-12") {
                attribute("step", "1")
                onInput { onChange(this.value?.toInt() ?: 0) }
                onChange { onChange(this.value?.toInt() ?: 0) }
            }
            span(className = "absolute right-4 text-base-content/50") {
                span(className = "icon-[heroicons--user-plus] size-5")
            }
        }
    }
}

@Composable
fun IComponent.DurationField(
    label: String,
    hours: Int,
    minutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
) {
    val currentStrings by strings
    div(className = "form-control w-full") {
        label(className = "label") { span(className = "label-text font-medium") { +label } }
        div(className = "flex gap-2") {
            div(className = "relative flex-1 items-center flex") {
                numeric(value = hours, min = 0, decimals = 0, className = "input input-bordered w-full pr-8") {
                    attribute("step", "1")
                    onInput { onHoursChange(value?.toInt() ?: 0) }
                    onChange { onHoursChange(value?.toInt() ?: 0) }
                }
                span(className = "absolute right-3 text-base-content/50 text-sm") { +currentStrings.hours }
            }
            div(className = "relative flex-1 items-center flex") {
                numeric(value = minutes, min = 0, max = 59, decimals = 0, className = "input input-bordered w-full pr-12") {
                    attribute("step", "1")
                    onInput { onMinutesChange(value?.toInt() ?: 0) }
                    onChange { onMinutesChange(value?.toInt() ?: 0) }
                }
                span(className = "absolute right-3 text-base-content/50 text-sm") { +currentStrings.minutes }
            }
        }
    }
}

@Composable
fun IComponent.AllowedPaymentsField(
    bankTransfer: Boolean,
    onSite: Boolean,
    onBankTransferChange: (Boolean) -> Unit,
    onOnSiteChange: (Boolean) -> Unit,
) {
    val currentStrings by strings
    div(className = "form-control w-full") {
        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.allowedPaymentsLabel } }
        div(className = "flex gap-4 mt-2") {
            label(className = "cursor-pointer label justify-start gap-2") {
                checkBox(value = bankTransfer, className = "checkbox checkbox-primary") {
                    onChange { onBankTransferChange(value) }
                }
                span(className = "label-text") { +currentStrings.bankTransfer }
            }
            label(className = "cursor-pointer label justify-start gap-2") {
                checkBox(value = onSite, className = "checkbox checkbox-primary") {
                    onChange { onOnSiteChange(value) }
                }
                span(className = "label-text") { +currentStrings.paymentOnSite }
            }
        }
    }
}

@Composable
fun IComponent.ReservationDeadlineSection(
    enabled: Boolean,
    typeIsHours: Boolean,
    hours: Int,
    daysBefore: Int,
    timeStr: String,
    message: String,
    onEnabledChange: (Boolean) -> Unit,
    onTypeChange: (Boolean) -> Unit,
    onHoursChange: (Int) -> Unit,
    onDaysBeforeChange: (Int) -> Unit,
    onTimeStrChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
) {
    val currentStrings by strings
    div(className = "card bg-base-100 shadow-sm") {
        div(className = "card-body") {
            h2(className = "card-title text-lg mb-2") { +currentStrings.reservationDeadlineSection }
            label(className = "cursor-pointer label justify-start gap-3") {
                checkBox(value = enabled, className = "checkbox checkbox-primary") {
                    onChange { onEnabledChange(value) }
                }
                span(className = "label-text") { +currentStrings.reservationDeadlineActive }
            }
            if (enabled) {
                div(className = "form-control w-full mt-2") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.reservationDeadlineTypeLabel }
                    }
                    select(className = "select select-bordered w-full") {
                        option(value = "hours", label = currentStrings.reservationDeadlineTypeHours) {
                            if (typeIsHours) selected(true)
                        }
                        option(value = "time", label = currentStrings.reservationDeadlineTypeTime) {
                            if (!typeIsHours) selected(true)
                        }
                        onChange { event ->
                            onTypeChange((event.target as? HTMLSelectElement)?.value == "hours")
                        }
                    }
                }
                if (typeIsHours) {
                    div(className = "form-control w-full mt-2") {
                        label(className = "label") {
                            span(className = "label-text font-medium") { +currentStrings.reservationDeadlineHoursLabel }
                        }
                        numeric(value = hours, min = 0, decimals = 0, className = "input input-bordered w-full") {
                            attribute("step", "1")
                            onInput { onHoursChange(value?.toInt() ?: 0) }
                            onChange { onHoursChange(value?.toInt() ?: 0) }
                        }
                    }
                } else {
                    div(className = "grid grid-cols-2 gap-4 mt-2") {
                        div(className = "form-control w-full") {
                            label(className = "label") {
                                span(className = "label-text font-medium") { +currentStrings.reservationDeadlineDaysBeforeLabel }
                            }
                            numeric(value = daysBefore, min = 0, decimals = 0, className = "input input-bordered w-full") {
                                attribute("step", "1")
                                onInput { onDaysBeforeChange(value?.toInt() ?: 0) }
                                onChange { onDaysBeforeChange(value?.toInt() ?: 0) }
                            }
                        }
                        div(className = "form-control w-full") {
                            label(className = "label") {
                                span(className = "label-text font-medium") { +currentStrings.reservationDeadlineTimeOfDayLabel }
                            }
                            text(value = timeStr, type = InputType.Time, className = "input input-bordered w-full") {
                                onInput { onTimeStrChange(value ?: "18:00") }
                            }
                        }
                    }
                }
                div(className = "form-control w-full mt-2") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.reservationDeadlineMessageLabel }
                    }
                    text(value = message, className = "input input-bordered w-full") {
                        placeholder(currentStrings.reservationDeadlineMessagePlaceholder)
                        onInput { onMessageChange(value ?: "") }
                    }
                }
            }
        }
    }
}

@Composable
fun IComponent.ShowAttendeeCountCheckbox(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    val currentStrings by strings
    div(className = "form-control w-full md:col-span-2") {
        p(className = "label-text font-medium mb-1") { +currentStrings.showAttendeeCount }
        label(className = "cursor-pointer label justify-start gap-3") {
            checkBox(value = value, className = "checkbox checkbox-primary") {
                onChange { onValueChange(value) }
            }
            span(className = "label-text text-sm text-base-content/70") { +currentStrings.showAttendeeCountHint }
        }
    }
}

