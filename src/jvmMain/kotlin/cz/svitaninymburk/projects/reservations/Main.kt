package cz.svitaninymburk.projects.reservations

import io.ktor.server.application.Application

/** Vstupní bod z `application.conf`. Aplikace = jádro rezervací + zapnuté moduly. */
fun Application.main() {
    reservationsServer(serverFeatures)
}
