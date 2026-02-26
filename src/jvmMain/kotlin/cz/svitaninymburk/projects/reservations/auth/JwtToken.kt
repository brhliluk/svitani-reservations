package cz.svitaninymburk.projects.reservations.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import cz.svitaninymburk.projects.reservations.user.User
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid


class JwtTokenService(
    private val secret: String, // V produkci načítat z ENV proměnných!
    private val issuer: String,
    private val audience: String
) {
    constructor(config: JwtConfig) : this(config.secret, config.issuer, config.audience)
    private val algorithm = Algorithm.HMAC256(secret)

    // Token platí např. 30 dní
    private val expirationTime = 30.days

    
    fun generateToken(user: User): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("id", user.id.toString())
            .withClaim("email", user.email)
            .withClaim("role", user.role.name)
            .withExpiresAt((Clock.System.now() + expirationTime).toJavaInstant())
            .sign(algorithm)
    }

    fun generateRefreshToken() = Uuid.random().toString()

    companion object {
        data class JwtConfig(
            val secret: String,
            val issuer: String,
            val audience: String,
            val realm: String
        )
    }
}