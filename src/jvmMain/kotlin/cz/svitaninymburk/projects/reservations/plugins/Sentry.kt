package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.BuildInfo
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.sentry.Sentry

/**
 * Sentry se zapíná jen když je v prostředí DSN (na serveru v /opt/reservations/.env).
 * Lokální běh DSN nemá, takže se SDK vůbec neinicializuje a chyby z vývoje
 * nechodí do produkčního projektu.
 */
fun Application.configureSentry() {
    val dsn = System.getenv("SENTRY_DSN")?.trim().orEmpty()
    if (dsn.isEmpty()) {
        log.info("Sentry vypnuto (SENTRY_DSN není nastaveno)")
        return
    }
    val release = System.getenv("SENTRY_RELEASE")?.takeIf { it.isNotBlank() }
        ?: "reservations@${BuildInfo.VERSION}"
    Sentry.init { options ->
        options.dsn = dsn
        // Bez tracingu se pomalý request (např. rezervace čekající na SMTP) v Sentry
        // vůbec neobjeví — výjimka nevznikne, jen to trvá. Provoz je malý, 100 % je OK.
        options.tracesSampleRate = 1.0
        options.environment = System.getenv("SENTRY_ENVIRONMENT")?.takeIf { it.isNotBlank() } ?: "production"
        options.release = release
        options.isDebug = false
    }
    log.info("Sentry zapnuto: release $release, environment ${System.getenv("SENTRY_ENVIRONMENT") ?: "production"}")
}
