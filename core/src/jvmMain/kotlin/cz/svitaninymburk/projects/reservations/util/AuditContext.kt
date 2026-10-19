package cz.svitaninymburk.projects.reservations.util

import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import kotlinx.coroutines.asContextElement
import kotlin.uuid.Uuid

/**
 * Ke které akci/rezervaci patří to, co se právě děje.
 *
 * Maily o tomhle samy nevědí — většina metod `EmailService` nese jen adresu a název
 * akce, ne UUID (viz `commonMain/service/Email.kt`). Doménová služba proto kolem
 * operace nastaví subjekt a `AuditingEmailService` si ho vyzvedne odsud.
 *
 * Vzor je stejný jako u [CallContextLocal]: `ThreadLocal.asContextElement()` je
 * `ThreadContextElement`, takže hodnota přežije i `withContext(Dispatchers.IO)`
 * uvnitř odesílání mailu.
 *
 * Aktéra řešit nemusí — ten se bere z JWT přes [currentCall].
 */
data class AuditSubject(
    val seriesId: Uuid? = null,
    val instanceId: Uuid? = null,
    val reservationId: Uuid? = null,
    val label: String = "",
)

val AuditContextLocal = ThreadLocal<AuditSubject>()

fun currentAuditSubject(): AuditSubject? = AuditContextLocal.get()

fun AuditSubject.asContextElement() = AuditContextLocal.asContextElement(value = this)

/** Spustí [block] s nastaveným subjektem, takže se do něj trefí i maily odeslané uvnitř. */
suspend fun <T> withAuditSubject(subject: AuditSubject, block: suspend () -> T): T =
    kotlinx.coroutines.withContext(subject.asContextElement()) { block() }

/**
 * Subjekt odvozený z cíle rezervace.
 *
 * U jednotlivé lekce kurzu se vyplní `seriesId` i `instanceId` — díky tomu vidí
 * detail kurzu i dění ve svých lekcích, aniž by se muselo joinovat.
 */
fun auditSubjectFor(
    target: ReservationTarget,
    reservationId: Uuid? = null,
): AuditSubject = when (target) {
    is ReservationTarget.Instance -> AuditSubject(
        seriesId = target.event.seriesId,
        instanceId = target.event.id,
        reservationId = reservationId,
        label = target.title,
    )
    is ReservationTarget.Series -> AuditSubject(
        seriesId = target.series.id,
        reservationId = reservationId,
        label = target.title,
    )
}
