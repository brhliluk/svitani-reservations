package cz.svitaninymburk.projects.reservations.util

object PhoneNumber {

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val hadPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        // Explicitní mezinárodní zápis: + nebo 00
        if (hadPlus) return withCountryCode(digits)
        if (digits.startsWith("00")) return withCountryCode(digits.substring(2))

        // Bez prefixu, ale nese CZ/SK předvolbu (např. "420123456789" nebo "421905123456")
        if ((digits.length == 11 || digits.length == 12) &&
            (digits.startsWith("420") || digits.startsWith("421"))
        ) {
            return withCountryCode(digits)
        }

        // Národní číslo: zahodit vedoucí trunk 0, default CZ
        val national = digits.trimStart('0')
        if (national.isEmpty()) return null
        return withCountryCode("420$national")
    }

    fun format(raw: String): String {
        val canonical = normalize(raw) ?: return raw
        val digits = canonical.removePrefix("+")
        val (cc, national) = splitCountryCode(digits)
        val grouped = national.chunked(3).joinToString(" ")
        return if (grouped.isEmpty()) "+$cc" else "+$cc $grouped"
    }

    fun isValid(raw: String): Boolean {
        val canonical = normalize(raw) ?: return false
        val digits = canonical.removePrefix("+")
        val (cc, national) = splitCountryCode(digits)
        return cc in listOf("420", "421") && national.length == 9
    }

    private fun withCountryCode(digits: String): String? {
        if (digits.length < 8) return null // odfiltruje kratší neúplné vstupy
        return "+$digits"
    }

    private fun splitCountryCode(digits: String): Pair<String, String> = when {
        digits.startsWith("420") || digits.startsWith("421") ->
            digits.substring(0, 3) to digits.substring(3)
        else ->
            digits.take(3) to digits.drop(3)
    }
}
