package cz.svitaninymburk.projects.reservations.util

import io.ktor.server.application.*
import kotlinx.coroutines.asContextElement

val CallContextLocal = ThreadLocal<ApplicationCall>()

suspend fun currentCall(): ApplicationCall? = CallContextLocal.get()

fun ApplicationCall.asContextElement() = CallContextLocal.asContextElement(value = this)