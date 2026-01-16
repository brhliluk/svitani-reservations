package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.UserError
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.rpc.annotations.RpcService


@RpcService
interface UserServiceInterface {
    suspend fun changeName(userId: String, name: String): Either<UserError.ChangeName, User>
    suspend fun changeSurname(userId: String, surname: String): Either<UserError.ChangeName, User>
    suspend fun changeEmail(userId: String, email: String): Either<UserError.ChangeEmail, User>
}

@RpcService
interface AdminUserServiceInterface {
    suspend fun raiseToAdmin(userId: String): Either<UserError.RaiseToAdmin, User>
    suspend fun downgradeToUser(userId: String): Either<UserError.DowngradeToUser, User>
}
