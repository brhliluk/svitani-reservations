package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.UserError
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.rpc.annotations.RpcService
import kotlin.uuid.Uuid


/**
 * Úpravy vlastního profilu. Identita chodí z JWT, ne parametrem — jinak by kterýkoli
 * přihlášený uživatel přepsal jméno nebo e-mail libovolného účtu, a u e-mailu je to
 * rovnou cesta k převzetí cizích rezervací (párují se podle adresy).
 */
@RpcService
interface UserServiceInterface {
    suspend fun changeName(name: String): Either<UserError.ChangeName, User>
    suspend fun changeSurname(surname: String): Either<UserError.ChangeName, User>
    suspend fun changeEmail(email: String): Either<UserError.ChangeEmail, User>
    suspend fun changePassword(oldPassword: String, newPassword: String): Either<UserError.ChangePassword, Unit>
}

@RpcService
interface AdminUserServiceInterface {
    suspend fun raiseToAdmin(userId: Uuid): Either<UserError.RaiseToAdmin, User>
    suspend fun downgradeToUser(userId: Uuid): Either<UserError.DowngradeToUser, User>
}
