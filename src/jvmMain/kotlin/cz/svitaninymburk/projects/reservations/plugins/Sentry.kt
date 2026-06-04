package cz.svitaninymburk.projects.reservations.plugins

import io.ktor.server.application.Application
import io.sentry.Sentry

fun Application.configureSentry() {
    Sentry.init { options ->
        options.dsn = "https://5e0bbfc7ae86f4cf1e53a3b6d6f49e2a@o4511506335334400.ingest.de.sentry.io/4511506346868816"
        // Set tracesSampleRate to 1.0 to capture 100% of transactions for tracing.
        // We recommend adjusting this value in production.
        options.tracesSampleRate = 1.0
        // When first trying Sentry it's good to see what the SDK is doing:
        options.isDebug = true
    }
}