package cz.svitaninymburk.projects.reservations.plugins

import io.ktor.server.application.Application
import io.sentry.Sentry

fun Application.configureSentry() {
    Sentry.init { options ->
        options.dsn = "https://5e0bbfc7ae86f4cf1e53a3b6d6f49e2a@o4511506335334400.ingest.de.sentry.io/4511506346868816"
        // Bez tracingu se pomalý request (např. rezervace čekající na SMTP) v Sentry
        // vůbec neobjeví — výjimka nevznikne, jen to trvá. Provoz je malý, 100 % je OK.
        options.tracesSampleRate = 1.0
        options.environment = System.getenv("SENTRY_ENVIRONMENT") ?: "production"
        System.getenv("SENTRY_RELEASE")?.let { options.release = it }
        options.isDebug = false
    }
}