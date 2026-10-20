package cz.svitaninymburk.projects.reservations.util

import cz.svitaninymburk.projects.reservations.user.User
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import kotlinx.coroutines.asContextElement

val CallContextLocal = ThreadLocal<ApplicationCall>()

suspend fun currentCall(): ApplicationCall? = CallContextLocal.get()

/** Volá právě admin? Podle role v JWT; bez přihlášení false. */
suspend fun isAdminCall(): Boolean =
    currentCall()?.principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString() == User.Role.ADMIN.name

fun ApplicationCall.asContextElement() = CallContextLocal.asContextElement(value = this)