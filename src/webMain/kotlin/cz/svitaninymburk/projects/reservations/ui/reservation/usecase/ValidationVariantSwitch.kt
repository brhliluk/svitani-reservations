package cz.svitaninymburk.projects.reservations.ui.reservation.usecase

import web.window.window

/**
 * DOČASNÉ: přepínač validace povinných vlastních polí přes URL, aby šlo obě
 * varianty proklikat na jedné běžící aplikaci ve dvou záložkách.
 *
 *   http://localhost:3000/                        → dnešní chování
 *   http://localhost:3000/?validation=proposed    → navrhované chování
 *
 * Až se jedna varianta vybere, tenhle soubor i `CustomFieldValidation` zmizí.
 */
val activeCustomFieldValidation: CustomFieldValidation
    get() = if (window.location.search.contains("validation=proposed")) {
        CustomFieldValidation.PROPOSED
    } else {
        CustomFieldValidation.CURRENT
    }
