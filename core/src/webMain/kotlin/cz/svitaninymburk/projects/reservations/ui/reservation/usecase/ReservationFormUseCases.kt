package cz.svitaninymburk.projects.reservations.ui.reservation.usecase

import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.PriceModifier
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import cz.svitaninymburk.projects.reservations.event.hoursFromRange
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.reservation.areCustomFieldsValid
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.util.PhoneNumber
import cz.svitaninymburk.projects.reservations.util.hoursBetween

/** Délka kódu peněženky. Teprve celý kód má smysl posílat na server k ověření. */
const val WALLET_CODE_LENGTH = 14

fun isCompleteWalletCode(code: String): Boolean = code.length == WALLET_CODE_LENGTH

// --- Peněženka a cena ---

/** Peněženka umí zaplatit nejvýš celou cenu — zbytek kreditu se nepřevádí. */
fun walletDeduction(balance: Double?, total: Double): Double =
    balance?.let { minOf(it, total) } ?: 0.0

fun remainingToPay(total: Double, deduction: Double): Double = total - deduction

/**
 * Pokryje-li peněženka celou cenu, jde rezervace jako [PaymentType.FREE] —
 * není co převádět ani vybírat na místě, takže by výběr platby jen mátl a
 * formulář ho v tom případě vůbec nezobrazuje.
 *
 * Platí to i pro akci zdarma: nula pokrytá nulou je taky "celá cena".
 */
fun effectivePaymentType(
    total: Double,
    deduction: Double,
    chosen: PaymentType,
): PaymentType = if (deduction == total) PaymentType.FREE else chosen

/** Výchozí způsob platby ve formuláři: první, který akce povoluje (FREE se nevybírá). */
fun initialPaymentType(allowed: List<PaymentType>): PaymentType =
    allowed.firstOrNull { it != PaymentType.FREE } ?: PaymentType.BANK_TRANSFER

fun showsPaymentTypePicker(total: Double, deduction: Double): Boolean =
    remainingToPay(total, deduction) > 0.0

/** Náhradník drží jedno místo, ať si do pole napsal cokoli. */
fun submittedSeatCount(asWaitlist: Boolean, seats: Int): Int = if (asWaitlist) 1 else seats

/**
 * Kolikrát se cena násobí délkou rezervace: u úseku (hlídání) jeho délka v hodinách,
 * jinak 1. Server počítá totéž (`createSlotReservation`), formulář jen ukazuje cenu předem.
 */
fun reservationTimeMultiplier(target: ReservationTarget): Double = when (target) {
    is ReservationTarget.Slot -> hoursBetween(target.startDateTime, target.endDateTime)
    is ReservationTarget.Instance, is ReservationTarget.Series -> 1.0
}

/**
 * Kolik hodin se v rozpadu ceny násobí. U úseku je to jeho délka. Jinak se bere první
 * časový rozsah, který cenu skutečně násobí ([PriceModifier.TimeMultiplier]) — ostatní
 * časová pole cenu neovlivňují, takže se v rozpadu nemají objevit. `null` = nic
 * k násobení, včetně případu, kdy rozsah ještě není vyplněný nebo je prázdný.
 */
fun timeMultiplierHours(
    target: ReservationTarget,
    customValues: Map<String, CustomFieldValue>,
): Double? {
    if (target is ReservationTarget.Slot) return reservationTimeMultiplier(target)
    val field = target.customFields
        .filterIsInstance<TimeRangeFieldDefinition>()
        .firstOrNull { it.priceModifier is PriceModifier.TimeMultiplier }
        ?: return null
    val range = (customValues[field.key] as? TimeRangeValue)?.takeIf { it.to > it.from } ?: return null
    return hoursFromRange(range)
}

/**
 * Hodiny v rozpadu ceny na jedno desetinné místo, bez zbytečné nuly:
 * 1.5 → "1.5", 2.0 → "2".
 */
fun formatPriceHours(hours: Double): String {
    val rounded = kotlin.math.round(hours * 10) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

// --- Validace ---

/**
 * Kontakt: jméno, příjmení, e-mail a telefon. E-mail se hlídá jen na zavináč —
 * důkladnější ověření dělá server, formulář jen nechce pustit očividný nesmysl.
 */
fun isContactValid(firstName: String, lastName: String, email: String, phone: String): Boolean =
    firstName.isNotBlank() &&
        lastName.isNotBlank() &&
        email.contains("@") &&
        PhoneNumber.isValid(phone)

fun isReservationFormValid(
    target: ReservationTarget?,
    firstName: String,
    lastName: String,
    email: String,
    phone: String,
    seats: Int,
    customValues: Map<String, CustomFieldValue>,
): Boolean =
    isContactValid(firstName, lastName, email, phone) &&
        seats > 0 &&
        (target == null || areCustomFieldsValid(target, customValues))

// --- UseCase třídy (tenké) ---

class WalletLookup(private val reservations: ReservationServiceInterface) {
    /** Nenalezená peněženka není chyba formuláře, jen se nic neodečte. */
    suspend fun info(code: String, email: String) =
        reservations.getWalletInfo(code, email).getOrNull()
}
