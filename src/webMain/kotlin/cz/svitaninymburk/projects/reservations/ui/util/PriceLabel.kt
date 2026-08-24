package cz.svitaninymburk.projects.reservations.ui.util

import cz.svitaninymburk.projects.reservations.i18n.AppStrings

/**
 * Cena k zobrazení. Nula je „Zdarma", ne „0.0 Kč" — u akcí zdarma nemá smysl
 * uvádět částku. Nenulové ceny záměrně nechává v původním formátu, aby se
 * zaokrouhlením neztratily koruny.
 */
fun totalPriceLabel(amount: Double, strings: AppStrings): String =
    if (amount <= 0.0) strings.free else "$amount ${strings.currency}"
