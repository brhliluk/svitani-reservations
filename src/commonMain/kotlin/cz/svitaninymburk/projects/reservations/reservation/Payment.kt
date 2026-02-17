package cz.svitaninymburk.projects.reservations.reservation

import kotlinx.serialization.Serializable
import kotlin.time.Instant


@Serializable
data class PaymentInfo(
    val reservationId: String,
    val amount: Double,
    val currency: String = "CZK",
    val variableSymbol: String,
    val constantSymbol: String = "0308",
    val beneficiaryAccount: String,
    val dueDateTime: Instant,
) {
    fun toSpaydString(): String {
        return "SPD*1.0*ACC:$beneficiaryAccount*AM:$amount*CC:$currency*X-VS:$variableSymbol*MSG:Rezervace $reservationId"
    }

    @Serializable
    enum class Type {
        BANK_TRANSFER, // QR kód
        ON_SITE,       // Na místě
        FREE           // Zdarma
    }
}