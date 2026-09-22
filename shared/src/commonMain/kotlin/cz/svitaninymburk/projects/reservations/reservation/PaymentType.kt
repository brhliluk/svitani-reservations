package cz.svitaninymburk.projects.reservations.reservation

import kotlinx.serialization.Serializable

/**
 * Jak se za rezervaci platí. Do DB i na drát jde jméno konstanty
 * (`enumerationByName`, resp. kotlinx), takže přejmenování typu se uložených dat netýká.
 */
@Serializable
enum class PaymentType {
    BANK_TRANSFER, // QR kód
    ON_SITE,       // Na místě
    FREE           // Zdarma
}
