package cz.svitaninymburk.projects.reservations.util

import java.math.BigInteger


object CzechIbanGenerator {

    /**
     * Converts a standard Czech account number to IBAN.
     * Supported formats:
     * - "1234567890/0100" (Account / Bank Code)
     * - "123456-1234567890/0100" (Prefix - Account / Bank Code)
     */
    fun toIban(localAccount: String): String {
        // 1. Basic validation and parsing
        val parts = localAccount.split("/")
        if (parts.size != 2) throw IllegalArgumentException("Invalid account format. Expected 'number/bank_code'")

        val accountPart = parts[0]
        val bankCode = parts[1]

        if (bankCode.length != 4) throw IllegalArgumentException("Bank code must be 4 digits")

        // 2. Parse Prefix and Account Number
        // Format can be "prefix-number" or just "number"
        val accountParts = accountPart.split("-")
        val (prefix, number) = if (accountParts.size == 2) {
            accountParts[0] to accountParts[1]
        } else {
            "" to accountParts[0]
        }

        // 3. Construct BBAN (Basic Bank Account Number) for Czechia
        // Structure: Bank Code (4) + Prefix (6, padded) + Number (10, padded) = 20 digits
        val bban = buildString {
            append(bankCode)
            append(prefix.padStart(6, '0'))
            append(number.padStart(10, '0'))
        }

        // 4. Calculate Check Digits (ISO 7064 Modulo 97)
        // Country code for Czechia is CZ.
        // Convert letters to numbers: C=12, Z=35.
        // We append "123500" (CZ + 00 placeholder) to the BBAN.
        val numericCountryCode = "123500"
        val numberForChecksum = bban + numericCountryCode

        // Calculate: 98 - (HugeNumber % 97)
        val bigInt = BigInteger(numberForChecksum)
        val remainder = bigInt.mod(BigInteger.valueOf(97)).toInt()
        val checkDigits = 98 - remainder

        // Pad check digits to 2 chars (e.g., 2 -> "02")
        val checkDigitsStr = checkDigits.toString().padStart(2, '0')

        // 5. Final Assembly
        return "CZ$checkDigitsStr$bban"
    }
}