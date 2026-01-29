package cz.svitaninymburk.projects.reservations.routing

import cz.svitaninymburk.projects.reservations.plugins.auth.AdminAuthorization
import cz.svitaninymburk.projects.reservations.service.AdminUserServiceInterface
import cz.svitaninymburk.projects.reservations.service.AuthenticatedEventServiceInterface
import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getServiceManager
import io.ktor.server.routing.Route


fun Route.adminRoutes() {
    install(AdminAuthorization)
    applyRoutes(getServiceManager<AdminUserServiceInterface>())
    applyRoutes(getServiceManager<AuthenticatedEventServiceInterface>())
}