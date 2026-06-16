package cz.svitaninymburk.projects.reservations.util

import io.ktor.util.logging.Logger
import io.sentry.Sentry
import io.sentry.SentryLevel

fun captureEmailError(logger: Logger, message: String) {
    logger.error(message)
    Sentry.captureMessage(message, SentryLevel.ERROR)
}
