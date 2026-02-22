package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.svitaninymburk.projects.reservations.error.UserError
import cz.svitaninymburk.projects.reservations.repository.user.UserRepository
import cz.svitaninymburk.projects.reservations.user.User
import kotlin.uuid.Uuid


class UserService(val userRepository: UserRepository): UserServiceInterface {

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
}

class AdminService(val userRepository: UserRepository): AdminUserServiceInterface {
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
