package cz.svitaninymburk.projects.reservations.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("user") sealed interface UserError : AppError {
    @Serializable @SerialName("update") sealed interface UpdateUser : UserError
    @Serializable @SerialName("raise_to_admin") sealed interface RaiseToAdmin : UserError
    @Serializable @SerialName("downgrade_to_user") sealed interface DowngradeToUser : UserError
    @Serializable @SerialName("change_name") sealed interface ChangeName : UserError
    @Serializable @SerialName("change_email") sealed interface ChangeEmail : UserError

    @Serializable data class UserNotFound(val id: String) : UpdateUser, RaiseToAdmin, ChangeName, ChangeEmail, DowngradeToUser
    @Serializable data class IdsDoNotMatch(val id: String, val userId: String) : UpdateUser
    @Serializable data object AdminAlready : RaiseToAdmin
}