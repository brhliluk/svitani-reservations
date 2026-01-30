package cz.svitaninymburk.projects.reservations.qr

import kotlin.text.iterator

object CzechIbanGenerator {

    fun toIban(localAccount: String): String {
        val cleanAccount = localAccount.filter { !it.isWhitespace() }

        val parts = cleanAccount.split("/")
        if (parts.size != 2) throw IllegalArgumentException("Invalid format: $localAccount")

        val accountPart = parts[0]
        val bankCode = parts[1]

        if (bankCode.length != 4) throw IllegalArgumentException("Bank code must be 4 digits")

        val accountParts = accountPart.split("-")
        val (prefix, number) = if (accountParts.size == 2) {
            accountParts[0] to accountParts[1]
        } else {
            "" to accountParts[0]
        }

        // BBAN (Bank Code + Prefix + Number)
        val bban = buildString {
            append(bankCode)
            append(prefix.padStart(6, '0'))
            append(number.padStart(10, '0'))
        }

        // Calculate Check Digits (MOD 97)
        // CZ = 1235, + 00 placeholder
        val numericCountryCode = "123500"
        val numberForChecksum = bban + numericCountryCode

        val remainder = modulo97(numberForChecksum)
        val checkDigits = 98 - remainder

        val checkDigitsStr = checkDigits.toString().padStart(2, '0')

        return "CZ$checkDigitsStr$bban"
    }

    private fun modulo97(numberStr: String): Int {
        var remainder = 0
        for (char in numberStr) {
            val digit = char.digitToInt()
            remainder = (remainder * 10 + digit) % 97
        }
        return remainder
    }
}