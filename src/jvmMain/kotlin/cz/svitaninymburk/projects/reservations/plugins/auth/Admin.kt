package cz.svitaninymburk.projects.reservations.plugins.auth

import cz.svitaninymburk.projects.reservations.user.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

val AdminAuthorization = createRouteScopedPlugin(name = "AdminAuthorization") {
    onCall { call ->
        val principal = call.principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()

        if (role != User.Role.ADMIN.name) {
            call.respond(HttpStatusCode.Forbidden, "Přístup odepřen: Vyžadována role administrátora")
            return@onCall
        }
    }
}