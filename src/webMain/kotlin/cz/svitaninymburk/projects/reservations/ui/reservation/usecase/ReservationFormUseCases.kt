package cz.svitaninymburk.projects.reservations.ui.reservation.usecase

import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.NumberValue
import cz.svitaninymburk.projects.reservations.event.PriceModifier
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import cz.svitaninymburk.projects.reservations.event.hoursFromRange
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.util.PhoneNumber

/** Délka kódu peněženky. Teprve celý kód má smysl posílat na server k ověření. */
const val WALLET_CODE_LENGTH = 14

fun isCompleteWalletCode(code: String): Boolean = code.length == WALLET_CODE_LENGTH

// --- Peněženka a cena ---

/** Peněženka umí zaplatit nejvýš celou cenu — zbytek kreditu se nepřevádí. */
fun walletDeduction(balance: Double?, total: Double): Double =
    balance?.let { minOf(it, total) } ?: 0.0

fun remainingToPay(total: Double, deduction: Double): Double = total - deduction

/**
 * Pokryje-li peněženka celou cenu, jde rezervace jako [PaymentInfo.Type.FREE] —
 * není co převádět ani vybírat na místě, takže by výběr platby jen mátl a
 * formulář ho v tom případě vůbec nezobrazuje.
 *
 * Platí to i pro akci zdarma: nula pokrytá nulou je taky "celá cena".
 */
fun effectivePaymentType(
    total: Double,
    deduction: Double,
    chosen: PaymentInfo.Type,
): PaymentInfo.Type = if (deduction == total) PaymentInfo.Type.FREE else chosen

fun showsPaymentTypePicker(total: Double, deduction: Double): Boolean =
    remainingToPay(total, deduction) > 0.0

/** Náhradník drží jedno místo, ať si do pole napsal cokoli. */
fun submittedSeatCount(asWaitlist: Boolean, seats: Int): Int = if (asWaitlist) 1 else seats

/**
 * Kolik hodin se v rozpadu ceny násobí. Bere se první časový rozsah, který cenu
 * skutečně násobí ([PriceModifier.TimeMultiplier]) — ostatní časová pole cenu
 * neovlivňují, takže se v rozpadu nemají objevit. `null` = nic k násobení,
 * včetně případu, kdy rozsah ještě není vyplněný nebo je prázdný.
 */
fun timeMultiplierHours(
    target: ReservationTarget,
    customValues: Map<String, CustomFieldValue>,
): Double? {
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

/**
 * Vlastní pole akce. Nepovinné pole projde vždy; u povinného se kontroluje
 * obsah podle typu:
 *
 *  - text musí mít neprázdný obsah — mezery se nepočítají,
 *  - číslo musí být vyplněné a v případě zadaných hranic i uvnitř nich
 *    (`imaskNumeric` u vstupu je jen pomůcka, `max` neomezuje),
 *  - zaškrtávátko musí být zaškrtnuté; povinný souhlas s podmínkami je přesně
 *    ten případ, pro který to platí,
 *  - časový rozsah musí být neprázdný (od < do) a celý uvnitř doby akce.
 *
 * Špatný typ hodnoty pro dané pole se bere jako nevyplněno. Nekontroluje se
 * `value != null`, ale skutečný obsah: `CustomFieldValue` jsou data classy, u
 * kterých `toString()` vrací `TextValue(fieldKey=..., value=)` a blank tedy
 * není nikdy — a `renderCustomField` zapisuje hodnotu do mapy při každém
 * stisku klávesy a nikdy ji neodebere, takže na přítomnost klíče se spolehnout
 * nelze.
 */
fun isCustomFieldValid(
    field: CustomFieldDefinition,
    value: CustomFieldValue?,
    target: ReservationTarget,
): Boolean {
    if (!field.isRequired) return true
    return when (field) {
        is TextFieldDefinition -> (value as? TextValue)?.value?.isNotBlank() == true
        is NumberFieldDefinition -> {
            val number = (value as? NumberValue)?.value ?: return false
            val aboveMin = field.min?.let { number >= it.toFloat() } ?: true
            val belowMax = field.max?.let { number <= it.toFloat() } ?: true
            aboveMin && belowMax
        }
        is BooleanFieldDefinition -> (value as? BooleanValue)?.value == true
        is TimeRangeFieldDefinition -> {
            val range = value as? TimeRangeValue ?: return false
            val window = target.startDateTime.time..target.endDateTime.time
            range.from < range.to && range.from in window && range.to in window
        }
    }
}

fun areCustomFieldsValid(
    target: ReservationTarget,
    values: Map<String, CustomFieldValue>,
): Boolean = target.customFields.all { isCustomFieldValid(it, values[it.key], target) }

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
