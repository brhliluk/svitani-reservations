package cz.svitaninymburk.projects.reservations.event

private val OWNER_EMAIL_SPLIT_REGEX = Regex("""[,;\s]+""")

// Znaky zakázané v adrese (RFC "specials") adresu vyřadí úplně — jinak by ji odmítl
// až SMTP výjimkou a notifikace vlastníkovi by se neodeslala.
private const val OWNER_EMAIL_CHAR = """[^\s@,;:<>()\[\]\\"]"""
private val OWNER_EMAIL_PATTERN = Regex("^$OWNER_EMAIL_CHAR+@$OWNER_EMAIL_CHAR+\\.$OWNER_EMAIL_CHAR+$")

fun parseOwnerEmails(rawEntries: List<String>): List<String> {
    val result = LinkedHashSet<String>()
    for (raw in rawEntries) {
        for (token in raw.split(OWNER_EMAIL_SPLIT_REGEX)) {
            val trimmed = token.trim()
            if (trimmed.isNotEmpty() && OWNER_EMAIL_PATTERN.matches(trimmed)) {
                result.add(trimmed)
            }
        }
    }
    return result.toList()
}
