package cz.svitaninymburk.projects.reservations

import io.ktor.server.application.Application
import io.ktor.server.application.log

/** Vstupní bod z `application.conf`. Aplikace = jádro rezervací + zapnuté moduly. */
fun Application.main() {
    log.info("Volitelné moduly: ${enabledFeatures.ifEmpty { listOf("žádné") }.joinToString()}")
    reservationsServer()
}
