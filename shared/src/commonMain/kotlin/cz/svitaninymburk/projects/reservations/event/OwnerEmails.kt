package cz.svitaninymburk.projects.reservations.event

private val OWNER_EMAIL_SPLIT_REGEX = Regex("[,\\s]+")
private val OWNER_EMAIL_PATTERN = Regex("^\\S+@\\S+\\.\\S+$")

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
