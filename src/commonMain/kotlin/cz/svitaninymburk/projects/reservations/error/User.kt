package cz.svitaninymburk.projects.reservations.error

sealed interface UserError : AppError {
    sealed interface UpdateUser : UserError
    sealed interface RaiseToAdmin : UserError
    sealed interface DowngradeToUser : UserError
    sealed interface ChangeName : UserError
    sealed interface ChangeEmail : UserError

    data class UserNotFound(val id: String) : UpdateUser, RaiseToAdmin, ChangeName, ChangeEmail, DowngradeToUser
    data class IdsDoNotMatch(val id: String, val userId: String) : UpdateUser
    data object AdminAlready : RaiseToAdmin
}