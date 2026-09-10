package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.repository.audit.AuditRepository
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Historie se drží rok zpátky; starší se denně maže. */
val AUDIT_RETENTION = 365.days

/**
 * Rolling okno nad `audit_events`.
 *
 * `VACUUM` se schválně nespouští — vyžaduje exkluzivní zámek, se kterým by
 * v WAL režimu a poolem o třech spojeních narazil na `SQLITE_BUSY`. Uvolněné
 * stránky se stejně recyklují pro nové záznamy, takže se soubor v ustáleném
 * stavu přestane zvětšovat i bez něj.
 */
fun Application.startAuditRetentionJob() {
    val auditRepository: AuditRepository by inject()

    launch(Dispatchers.IO) {
        while (isActive) {
            runCatching {
                val cutoff = Clock.System.now() - AUDIT_RETENTION
                val smazano = auditRepository.deleteOlderThan(cutoff)
                if (smazano > 0) {
                    println("ℹ️ audit log: smazáno $smazano záznamů starších než rok")
                }
            }.onFailure { e ->
                println("⚠️ AuditRetention job error: ${e.message}")
            }
            delay(24.hours)
        }
    }
}
