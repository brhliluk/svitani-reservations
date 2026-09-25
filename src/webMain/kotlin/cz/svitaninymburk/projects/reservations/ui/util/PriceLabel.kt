package cz.svitaninymburk.projects.reservations.ui.util

import cz.svitaninymburk.projects.reservations.i18n.AppStrings
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Částka bez zbytečné nuly: 150.0 → "150", 150.5 → "150.5", 33.333 → "33.33".
 * Vlastní formát, protože Double.toString() dává v JS "150" a ve wasm "150.0";
 * a nezaokrouhluje na celé koruny, aby se v přehledech neztrácely haléře.
 */
fun formatAmountNumber(amount: Double): String {
    val cents = (amount * 100).roundToLong()
    val sign = if (cents < 0) "-" else ""
    val whole = abs(cents) / 100
    val rest = abs(cents) % 100
    return when {
        rest == 0L -> "$sign$whole"
        rest % 10 == 0L -> "$sign$whole.${rest / 10}"
        else -> "$sign$whole.${rest.toString().padStart(2, '0')}"
    }
}

fun formatAmount(amount: Double, strings: AppStrings): String =
    "${formatAmountNumber(amount)} ${strings.currency}"

/** Pohyb v peněžence: „+50 Kč“, „−50 Kč“. */
fun signedAmount(amount: Double, strings: AppStrings): String = when {
    amount > 0.0 -> "+${formatAmount(amount, strings)}"
    amount < 0.0 -> "−${formatAmount(-amount, strings)}"
    else -> formatAmount(0.0, strings)
}

/** Cena k zobrazení. Nula je „Zdarma", ne „0 Kč" — u akcí zdarma nemá smysl uvádět částku. */
fun totalPriceLabel(amount: Double, strings: AppStrings): String =
    if (amount <= 0.0) strings.free else formatAmount(amount, strings)
