package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentType

val PaymentType.label: String get() {
    val currentStrings by strings
    return when (this) {
        PaymentType.BANK_TRANSFER -> currentStrings.bankTransfer
        PaymentType.ON_SITE -> currentStrings.onSite
        PaymentType.FREE -> currentStrings.free
    }
}