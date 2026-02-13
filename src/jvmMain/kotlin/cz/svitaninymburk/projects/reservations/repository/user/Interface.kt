package cz.svitaninymburk.projects.reservations.repository.user

import cz.svitaninymburk.projects.reservations.user.User

interface UserRepository {
    suspend fun findByEmail(email: String): User?
    suspend fun findById(id: String): User?
    suspend fun findByResetToken(token: String): User.Email?
    suspend fun create(user: User): User
    suspend fun update(userId: String, user: User): User
    suspend fun linkGoogleAccount(userId: String, googleSub: String): User.Google
}
