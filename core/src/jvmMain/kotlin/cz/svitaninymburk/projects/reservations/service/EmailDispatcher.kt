package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.util.AuditContextLocal
import cz.svitaninymburk.projects.reservations.util.captureEmailError
import cz.svitaninymburk.projects.reservations.util.currentAuditSubject
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.jvm.jvmName

/**
 * Kudy odchází maily vzhledem k požadavku, který je vyvolal.
 *
 * Odesílání jde přes SMTP a trvá sekundy — na produkci se mezi uložením rezervace
 * a posledním odeslaným mailem naměřilo 3,4 až 12,6 s (medián ~5,5 s). Celou tu
 * dobu koukal zákazník na odesílající se formulář, přestože na výsledku odeslání
 * nic nezávisí: volající ho jen zaloguje přes `captureEmailError`.
 */
interface EmailDispatcher {
    suspend fun dispatch(block: suspend () -> Unit)
}

/**
 * Odešle mimo požadavek, takže odpověď na volajícího nečeká.
 *
 * [scope] je aplikační, ne request — v `Application` scope běží i ostatní úlohy na
 * pozadí (`plugins/Payment.kt`, `WalletReset.kt`). Request scope by se po odeslání
 * odpovědi zrušila a maily s ní.
 *
 * Aktér i subjekt se vyhodnotí **teď**, dokud ještě platí kontext požadavku, a do
 * odloženého bloku se přenesou jako context elementy: `currentCall()` uvnitř už
 * nic nevrátí a historie by jinak místo admina hlásila „systém“.
 *
 * Co tohle vědomě neřeší: maily rozeslané při restartu služby nepřežijí. Na to by
 * byla potřeba outbox tabulka; při jednom selhání na 303 odeslání je zatím
 * levnější pojistkou tlačítko „poslat znovu“ v historii ([EmailResendService]).
 */
class BackgroundEmailDispatcher(
    private val scope: CoroutineScope,
    private val audit: AuditService,
) : EmailDispatcher {

    private val logger = KtorSimpleLogger(this::class.jvmName)

    override suspend fun dispatch(block: suspend () -> Unit) {
        val actor = audit.currentActor()
        val subject = currentAuditSubject()

        val carried: CoroutineContext = AuditService.ActorContextLocal.asContextElement(actor) +
            (subject?.let { AuditContextLocal.asContextElement(it) } ?: EmptyCoroutineContext)

        scope.launch(Dispatchers.IO + carried) {
            // Na pozadí není komu výjimku vrátit — bez odchycení by shodila scope
            // a zbylé maily z téhož požadavku by se neodeslaly vůbec.
            runCatching { block() }.onFailure {
                captureEmailError(logger, "Background email dispatch failed: ${it.message}")
            }
        }
    }
}

/**
 * Odešle rovnou, jako by žádné oddělení nebylo.
 *
 * Výchozí volba v konstruktorech služeb, aby testy mohly hned po zavolání operace
 * tvrdit, co se odeslalo — s odloženým odesíláním by závodily s vlastním vláknem.
 */
object InlineEmailDispatcher : EmailDispatcher {
    override suspend fun dispatch(block: suspend () -> Unit) = block()
}
