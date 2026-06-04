package cz.svitaninymburk.projects.reservations.plugins

import io.ktor.server.application.Application
import io.sentry.Sentry

fun Application.configureSentry() {
    Sentry.init { options ->
        options.dsn = "https://5e0bbfc7ae86f4cf1e53a3b6d6f49e2a@o4511506335334400.ingest.de.sentry.io/4511506346868816"
        options.tracesSampleRate = 0.0
        options.isDebug = false
    }
}