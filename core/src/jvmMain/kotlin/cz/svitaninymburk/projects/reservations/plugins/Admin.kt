package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.user.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

val AdminAuthorization = createRouteScopedPlugin(name = "AdminAuthorization") {
    on(AuthenticationChecked) { call ->
        val principal = call.principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()

        println("🔐 AdminAuthorization Check: Uživatel má roli: $role")
        if (role != User.Role.ADMIN.name) {
            call.respond(HttpStatusCode.Forbidden, "Přístup odepřen: Vyžadována role administrátora")
        }
    }
}