package cz.svitaninymburk.projects.reservations.auth

import org.mindrot.jbcrypt.BCrypt

interface HashingService {
    fun generateSaltedHash(value: String): String
    fun verify(value: String, saltedHash: String): Boolean
}

class BCryptHashingService : HashingService {
    override fun generateSaltedHash(value: String): String {
        return BCrypt.hashpw(value, BCrypt.gensalt())
    }

    override fun verify(value: String, saltedHash: String): Boolean {
        return BCrypt.checkpw(value, saltedHash)
    }
}