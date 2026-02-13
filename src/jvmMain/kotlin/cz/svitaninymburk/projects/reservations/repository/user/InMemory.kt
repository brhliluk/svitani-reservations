package cz.svitaninymburk.projects.reservations.repository.user

import cz.svitaninymburk.projects.reservations.user.User
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class InMemoryUserRepository : UserRepository {

    private val users = ConcurrentHashMap<String, User>()

    override suspend fun findByEmail(email: String): User? {
        return users.values.find { it.email == email }
    }

    override suspend fun findById(id: String): User? {
        return users[id]
    }

    override suspend fun findByResetToken(token: String): User.Email? {
        return users.values.find { it is User.Email && it.passwordResetToken == token } as User.Email?
    }

    override suspend fun create(user: User): User {
        users[user.id] = user
        return user
    }

    override suspend fun update(userId: String, user: User): User {
        users[userId] = user
        return user
    }


    override suspend fun linkGoogleAccount(userId: String, googleSub: String): User.Google {
        val user = users[userId] ?: throw IllegalStateException("User not found inside repo logic")

        val updatedUser = when (user) {
            is User.Email -> user.toGoogle(googleSub)
            is User.Google -> user.copy(googleSub = googleSub)
        }
        users[userId] = updatedUser
        return updatedUser
    }
}