package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.svitaninymburk.projects.reservations.auth.HashingService
import cz.svitaninymburk.projects.reservations.error.UserError
import cz.svitaninymburk.projects.reservations.repository.user.UserRepository
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.util.currentCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import kotlin.uuid.Uuid


open class UserService(
    val userRepository: UserRepository,
    val hashingService: HashingService,
) : UserServiceInterface {

    internal open suspend fun currentUserId(): Uuid? =
        currentCall()
            ?.principal<JWTPrincipal>()
            ?.payload
            ?.getClaim("id")
            ?.asString()
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }

    override suspend fun changeName(userId: Uuid, name: String): Either<UserError.ChangeName, User> = either {
        val user = ensureNotNull(userRepository.findById(userId)) { UserError.UserNotFound(userId.toString()) }
        userRepository.update(user.id, when (user) {
            is User.Email -> user.copy(name = name)
            is User.Google -> user.copy(name = name)
        })
    }

    override suspend fun changeSurname(userId: Uuid, surname: String): Either<UserError.ChangeName, User> = either {
        val user = ensureNotNull(userRepository.findById(userId)) { UserError.UserNotFound(userId.toString()) }
        userRepository.update(user.id, when (user) {
            is User.Email -> user.copy(surname = surname)
            is User.Google -> user.copy(surname = surname)
        })
    }

    override suspend fun changeEmail(userId: Uuid, email: String): Either<UserError.ChangeEmail, User> = either {
        val user = ensureNotNull(userRepository.findById(userId)) { UserError.UserNotFound(userId.toString()) }
        userRepository.update(user.id, when (user) {
            is User.Email -> user.copy(email = email)
            is User.Google -> user.copy(email = email)
        })
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): Either<UserError.ChangePassword, Unit> = either {
        ensure(newPassword.length >= 6) { UserError.WeakPassword }
        val userId = ensureNotNull(currentUserId()) { UserError.UserNotFound("") }
        val user = ensureNotNull(userRepository.findById(userId)) { UserError.UserNotFound(userId.toString()) }
        ensure(user is User.Email) { UserError.NotEmailUser }
        ensure(hashingService.verify(oldPassword, user.passwordHash)) { UserError.WrongOldPassword }
        val newHash = hashingService.generateSaltedHash(newPassword)
        userRepository.update(user.id, user.copy(passwordHash = newHash))
        Unit
    }
}

class AdminService(val userRepository: UserRepository) : AdminUserServiceInterface {
    override suspend fun raiseToAdmin(userId: Uuid): Either<UserError.RaiseToAdmin, User> = either {
        val user = ensureNotNull(userRepository.findById(userId)) { UserError.UserNotFound(userId.toString()) }
        ensure(user.role != User.Role.ADMIN) { UserError.AdminAlready }
        userRepository.update(user.id, when (user) {
            is User.Email -> user.copy(role = User.Role.ADMIN)
            is User.Google -> user.copy(role = User.Role.ADMIN)
        })
    }

    override suspend fun downgradeToUser(userId: Uuid): Either<UserError.DowngradeToUser, User> = either {
        val user = ensureNotNull(userRepository.findById(userId)) { UserError.UserNotFound(userId.toString()) }
        userRepository.update(user.id, when (user) {
            is User.Email -> user.copy(role = User.Role.USER)
            is User.Google -> user.copy(role = User.Role.USER)
        })
    }
}
