package cz.svitaninymburk.projects.reservations.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import cz.svitaninymburk.projects.reservations.auth.JwtTokenService
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
    val jwtConfig by inject<JwtTokenService.Companion.JwtConfig>()

    authentication {
        jwt("auth-jwt") {
            realm = jwtConfig.realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtConfig.secret))
                    .withAudience(jwtConfig.audience)
                    .withIssuer(jwtConfig.issuer)
                    .build()
            )

            validate { credential ->
                if (credential.payload.getClaim("id").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            authHeader { call ->
                val cookieValue = call.request.cookies["auth_token"]

                if (cookieValue != null) {
                    try { parseAuthorizationHeader("Bearer $cookieValue") }
                    catch (_: Exception) { null }
                } else {
                    call.request.parseAuthorizationHeader()
                }
            }
        }
    }
}