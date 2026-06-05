package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.i18n.ErrorStrings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("user") sealed interface UserError : AppError {
    @Serializable @SerialName("update") sealed interface UpdateUser : UserError
    @Serializable @SerialName("raise_to_admin") sealed interface RaiseToAdmin : UserError
    @Serializable @SerialName("downgrade_to_user") sealed interface DowngradeToUser : UserError
    @Serializable @SerialName("change_name") sealed interface ChangeName : UserError
    @Serializable @SerialName("change_email") sealed interface ChangeEmail : UserError
    @Serializable @SerialName("change_password") sealed interface ChangePassword : UserError

    @Serializable data class UserNotFound(val id: String) : UpdateUser, RaiseToAdmin, ChangeName, ChangeEmail, DowngradeToUser, ChangePassword
    @Serializable data class IdsDoNotMatch(val id: String, val userId: String) : UpdateUser
    @Serializable data object AdminAlready : RaiseToAdmin
    @Serializable data object NotEmailUser : ChangePassword
    @Serializable data object WrongOldPassword : ChangePassword
    @Serializable data object WeakPassword : ChangePassword
}

fun UserError.ChangePassword.localizedMessage(strings: ErrorStrings): String = when (this) {
    is UserError.UserNotFound -> strings.errorUserNotFound
    is UserError.NotEmailUser -> strings.errorNotEmailUser
    is UserError.WrongOldPassword -> strings.errorWrongOldPassword
    is UserError.WeakPassword -> strings.errorPasswordTooWeak
}
