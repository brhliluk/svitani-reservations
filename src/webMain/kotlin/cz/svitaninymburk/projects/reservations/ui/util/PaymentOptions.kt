package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo

val PaymentInfo.Type.label: String get() {
    val currentStrings by strings
    return when (this) {
        PaymentInfo.Type.BANK_TRANSFER -> currentStrings.bankTransfer
        PaymentInfo.Type.ON_SITE -> currentStrings.onSite
        PaymentInfo.Type.FREE -> currentStrings.free
    }
}