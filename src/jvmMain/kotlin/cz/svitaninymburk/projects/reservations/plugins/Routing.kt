package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.routing.adminRoutes
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.service.RefreshTokenServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.service.UserServiceInterface
import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getServiceManager
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Application.configureRouting() {
    routing {
        install(CallContextPlugin)
        applyRoutes(getServiceManager<EventServiceInterface>(), RpcSerializersModules)

        authenticate("auth-jwt") {

            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString()
                val email = principal?.payload?.getClaim("email")?.asString()

                call.respond(mapOf("id" to userId, "email" to email))
            }

            applyRoutes(getServiceManager<RefreshTokenServiceInterface>(), RpcSerializersModules)
            applyRoutes(getServiceManager<AuthenticatedReservationServiceInterface>(), RpcSerializersModules)
            applyRoutes(getServiceManager<UserServiceInterface>(), RpcSerializersModules)
            adminRoutes()
        }

        authenticate("auth-jwt", optional = true) {
            applyRoutes(getServiceManager<AuthServiceInterface>(), RpcSerializersModules)
            applyRoutes(getServiceManager<ReservationServiceInterface>(), RpcSerializersModules)
        }
    }
}
