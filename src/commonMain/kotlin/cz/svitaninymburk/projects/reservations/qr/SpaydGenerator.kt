package cz.svitaninymburk.projects.reservations.qr

import kotlin.math.roundToInt

object SpaydGenerator {
    fun generate(iban: String, amount: Double, vs: String?, message: String?, currency: String = "CZK"): String = buildString {
        append("SPD*1.0")
        append("*ACC:$iban")

        val amountInCents = (amount * 100).roundToInt()
        val wholePart = amountInCents / 100
        val decimalPart = amountInCents % 100
        append("*AM:$wholePart.${decimalPart.toString().padStart(2, '0')}")

        append("*CC:$currency")

        if (!vs.isNullOrBlank() && vs.all { it.isDigit() }) {
            append("*X-VS:$vs")
        }

        if (!message.isNullOrBlank()) {
            // SPAYD nemá rád diakritiku. V KMP je těžké ji odstranit bez externí lib.
            // Prozatím jen uppercase, ideálně bys měl mít funkci removeDiacritics()
            val safeMsg = message.take(60).uppercase()
            // .replace(Regex("[^A-Z0-9 ]"), "") // Volitelně vyhodit divné znaky
            append("*MSG:$safeMsg")
        }
    }
}