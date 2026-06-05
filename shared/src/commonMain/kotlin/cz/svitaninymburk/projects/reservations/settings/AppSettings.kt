package cz.svitaninymburk.projects.reservations.settings

import kotlinx.serialization.Serializable

data class AppSettings(
    val bankAccountNumber: String,
    val fioToken: String,
    val senderEmail: String,
    val gmailAppPassword: String,
    val senderDisplayName: String,
    val seasonResetMonth: Int = 6,
    val seasonResetDay: Int = 30,
    val walletResetWarningDays: Int = 7,
)

@Serializable
data class AppSettingsDisplayDto(
    val bankAccountNumber: String,
    val fioTokenMasked: String,
    val senderEmail: String,
    val gmailPasswordMasked: String,
    val senderDisplayName: String,
    val seasonResetMonth: Int,
    val seasonResetDay: Int,
    val walletResetWarningDays: Int,
)

@Serializable
data class UpdateSettingsRequest(
    val bankAccountNumber: String,
    val fioToken: String?,           // null = keep current stored value
    val senderEmail: String,
    val gmailAppPassword: String?,   // null = keep current stored value
    val senderDisplayName: String,
    val seasonResetMonth: Int = 6,
    val seasonResetDay: Int = 30,
    val walletResetWarningDays: Int = 7,
)

fun maskSecret(secret: String): String {
    if (secret.isEmpty()) return "•••"
    val prefix = secret.take(3)
    val remaining = secret.drop(prefix.length)
    val suffix = remaining.takeLast(minOf(3, maxOf(0, remaining.length)))
    val bulletCount = maxOf(3, 9 - prefix.length - suffix.length)
    return prefix + "•".repeat(bulletCount) + suffix
}
