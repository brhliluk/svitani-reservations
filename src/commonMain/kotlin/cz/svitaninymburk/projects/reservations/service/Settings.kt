package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.SettingsError
import cz.svitaninymburk.projects.reservations.settings.AppSettingsDisplayDto
import cz.svitaninymburk.projects.reservations.settings.UpdateSettingsRequest
import dev.kilua.rpc.annotations.RpcService

@RpcService
interface AppSettingsServiceInterface {
    suspend fun getSettings(): Either<SettingsError, AppSettingsDisplayDto>
    suspend fun testEmailSettings(
        senderEmail: String,
        appPassword: String?,
        displayName: String,
    ): Either<SettingsError, Unit>
    suspend fun testFioSettings(fioToken: String?): Either<SettingsError, Unit>
    suspend fun saveSettings(request: UpdateSettingsRequest): Either<SettingsError, Unit>
}
