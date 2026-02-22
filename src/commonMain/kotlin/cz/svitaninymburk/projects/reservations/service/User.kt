package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.UserError
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.rpc.annotations.RpcService
import kotlin.uuid.Uuid


@RpcService
interface UserServiceInterface {
    suspend fun changeName(userId: Uuid, name: String): Either<UserError.ChangeName, User>
    suspend fun changeSurname(userId: Uuid, surname: String): Either<UserError.ChangeName, User>
    suspend fun changeEmail(userId: Uuid, email: String): Either<UserError.ChangeEmail, User>
}

@RpcService
interface AdminUserServiceInterface {
    suspend fun raiseToAdmin(userId: Uuid): Either<UserError.RaiseToAdmin, User>
    suspend fun downgradeToUser(userId: Uuid): Either<UserError.DowngradeToUser, User>
}
