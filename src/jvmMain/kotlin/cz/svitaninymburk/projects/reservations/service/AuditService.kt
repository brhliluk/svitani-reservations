package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.audit.AuditOutcome
import cz.svitaninymburk.projects.reservations.repository.audit.AuditRepository
import cz.svitaninymburk.projects.reservations.repository.audit.NewAuditEvent
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.util.AuditSubject
import cz.svitaninymburk.projects.reservations.util.currentAuditSubject
import cz.svitaninymburk.projects.reservations.util.currentCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.util.logging.KtorSimpleLogger
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlin.reflect.jvm.jvmName
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Zapisuje do historie událostí.
 *
 * Dvě pravidla, na kterých to stojí:
 *  - **nikdy neshodí doménovou operaci** — selhání zápisu se jen ohlásí do Sentry,
 *    rezervace ani platba kvůli logu nespadne;
 *  - aktér se odvodí z JWT, subjekt (ke které akci to patří) z [AuditSubject],
 *    takže volající většinou nemusí předávat nic navíc.
 */
class AuditService(private val repository: AuditRepository) {

    private val logger = KtorSimpleLogger(this::class.jvmName)

    suspend fun record(
        type: AuditEventType,
        subjectLabel: String,
        seriesId: Uuid? = null,
        instanceId: Uuid? = null,
        reservationId: Uuid? = null,
        outcome: AuditOutcome? = null,
        recipient: String? = null,
        amount: Double? = null,
        detail: String? = null,
        actor: Actor? = null,
        occurredAt: Instant? = null,
    ) {
        val subject = currentAuditSubject()
        val resolvedActor = actor ?: currentActor()
        failSoft {
            repository.record(
                NewAuditEvent(
                    type = type,
                    actorType = resolvedActor.type,
                    actorLabel = resolvedActor.label,
                    subjectLabel = subjectLabel.ifBlank { subject?.label.orEmpty() },
                    seriesId = seriesId ?: subject?.seriesId,
                    instanceId = instanceId ?: subject?.instanceId,
                    reservationId = reservationId ?: subject?.reservationId,
                    outcome = outcome,
                    recipient = recipient,
                    amount = amount,
                    detail = detail,
                    occurredAt = occurredAt,
                )
            )
        }
    }

    data class Actor(val type: AuditActorType, val label: String)

    /**
     * Kdo operaci vyvolal. Mimo HTTP call (plánované joby, párování plateb z FIO)
     * není žádný principal — takové akce jsou [AuditActorType.SYSTEM].
     */
    suspend fun currentActor(): Actor {
        val payload = currentCall()?.principal<JWTPrincipal>()?.payload
            ?: return Actor(AuditActorType.SYSTEM, SYSTEM_LABEL)
        val email = payload.getClaim("email")?.asString().orEmpty()
        val role = payload.getClaim("role")?.asString()
        val type = if (role == User.Role.ADMIN.name) AuditActorType.ADMIN else AuditActorType.CUSTOMER
        return Actor(type, email.ifBlank { ANONYMOUS_LABEL })
    }

    private inline fun failSoft(block: () -> Unit) {
        runCatching { block() }.onFailure { e ->
            val message = "Audit log write failed: ${e.message}"
            logger.error(message)
            Sentry.captureMessage(message, SentryLevel.ERROR)
        }
    }

    companion object {
        const val SYSTEM_LABEL = "systém"
        const val ANONYMOUS_LABEL = "nepřihlášený"
    }
}
