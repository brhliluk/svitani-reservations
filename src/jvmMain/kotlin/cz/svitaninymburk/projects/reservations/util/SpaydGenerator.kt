package cz.svitaninymburk.projects.reservations.util


object SpaydGenerator {
    /**
     * Builds a string for the Czech QR payment standard (SPAYD).
     * @param iban Beneficiary account number in IBAN format (no spaces).
     * @param amount Payment amount (e.g., 350.0).
     * @param vs Variable symbol (must be numeric, max 10 digits).
     * @param message Message for the recipient.
     */
    fun generate(iban: String, amount: Double, vs: String?, message: String?, currency: String = "CZK"): String = buildString {
        append("SPD*1.0")

        // Account Number
        append("*ACC:$iban")

        // Amount: Must use a dot as a decimal separator (e.g. 100.50)
        // We replace comma with dot just to be safe regarding system Locale
        append("*AM:${String.format("%.2f", amount).replace(',', '.')}")

        // Currency: Fixed to CZK for this use case
        append("*CC:$currency")

        // Variable Symbol (optional but recommended for matching payments)
        if (!vs.isNullOrBlank()) {
            // Ensure the VS contains only digits, otherwise banking apps might reject it
            if (vs.all { it.isDigit() }) {
                append("*X-VS:$vs")
            }
        }

        // Message for recipient (optional)
        if (!message.isNullOrBlank()) {
            // SPAYD requires specific encoding, generally uppercase without diacritics is safest
            val safeMsg = message.take(60).uppercase()
            append("*MSG:$safeMsg")
        }
    }
}