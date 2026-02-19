package cz.svitaninymburk.projects.reservations.repository.user

import cz.svitaninymburk.projects.reservations.user.User
import kotlin.uuid.Uuid

interface UserRepository {
    suspend fun findByEmail(email: String): User?
    suspend fun findById(id: Uuid): User?
    suspend fun findByResetToken(token: String): User.Email?
    suspend fun create(user: User): User
    suspend fun update(userId: Uuid, user: User): User
    suspend fun linkGoogleAccount(userId: Uuid, googleSub: String): User.Google
}
