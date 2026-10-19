package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.service.SeriesScheduleRefresher
import io.ktor.server.application.Application
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

/**
 * Stored series schedule fields may predate the derived-cache invariant (or the
 * DB may have been edited by hand), so recompute all of them from actual
 * lessons once at startup. Idempotent.
 */
fun Application.startSeriesScheduleBackfill() {
    val refresher: SeriesScheduleRefresher by inject()
    val logger = KtorSimpleLogger("SeriesScheduleBackfill")
    launch(Dispatchers.IO) {
        try {
            refresher.refreshAll()
            logger.info("Series schedule backfill completed")
        } catch (e: Exception) {
            logger.error("Series schedule backfill failed", e)
        }
    }
}
