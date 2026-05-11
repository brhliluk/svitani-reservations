package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.i18n.ErrorStrings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("settings") sealed interface SettingsError : AppError {
    @Serializable @SerialName("load_failed") data object LoadFailed : SettingsError
    @Serializable @SerialName("save_failed") data object SaveFailed : SettingsError
    @Serializable @SerialName("email_test_failed") data class EmailTestFailed(val reason: String) : SettingsError
    @Serializable @SerialName("fio_test_failed") data class FioTestFailed(val reason: String) : SettingsError
    @Serializable @SerialName("encryption_key_missing") data object EncryptionKeyMissing : SettingsError
}

fun SettingsError.localizedMessage(strings: ErrorStrings): String = when (this) {
    is SettingsError.LoadFailed -> strings.errorSettingsLoadFailed
    is SettingsError.SaveFailed -> strings.errorSettingsSaveFailed
    is SettingsError.EmailTestFailed -> strings.errorSettingsEmailTestFailed(reason)
    is SettingsError.FioTestFailed -> strings.errorSettingsFioTestFailed(reason)
    is SettingsError.EncryptionKeyMissing -> strings.errorSettingsEncryptionKeyMissing
}
