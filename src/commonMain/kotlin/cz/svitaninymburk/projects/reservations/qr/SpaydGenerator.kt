package cz.svitaninymburk.projects.reservations.qr

import kotlin.math.roundToInt

private fun String.removeDiacritics(): String {
    val map = mapOf(
        'á' to 'a', 'č' to 'c', 'ď' to 'd', 'é' to 'e', 'ě' to 'e',
        'í' to 'i', 'ň' to 'n', 'ó' to 'o', 'ř' to 'r', 'š' to 's',
        'ť' to 't', 'ú' to 'u', 'ů' to 'u', 'ý' to 'y', 'ž' to 'z',
        'Á' to 'A', 'Č' to 'C', 'Ď' to 'D', 'É' to 'E', 'Ě' to 'E',
        'Í' to 'I', 'Ň' to 'N', 'Ó' to 'O', 'Ř' to 'R', 'Š' to 'S',
        'Ť' to 'T', 'Ú' to 'U', 'Ů' to 'U', 'Ý' to 'Y', 'Ž' to 'Z',
    )
    return map { map.getOrElse(it) { it } }.joinToString("")
}

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
            val safeMsg = message.take(60).removeDiacritics().uppercase().replace(Regex("[^A-Z0-9 ]"), "")
            append("*MSG:$safeMsg")
        }
    }
}