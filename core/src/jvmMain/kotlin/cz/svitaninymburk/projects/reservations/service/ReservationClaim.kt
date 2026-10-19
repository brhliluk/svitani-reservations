package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.repository.claim.ReservationClaimToken
import cz.svitaninymburk.projects.reservations.repository.claim.ReservationClaimTokenRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.user.UserRepository
import cz.svitaninymburk.projects.reservations.reservation.ClaimResult
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.util.auditSubjectFor
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Jak dlouho platí odkaz z mailu. Delší než u resetu hesla — člověk ho typicky otevře až večer. */
private val CLAIM_LINK_VALIDITY = 24.hours

/** Dřív než za tuhle dobu se stejnému uživateli druhý mail neposílá, ať se schránka nezaplní. */
private val CLAIM_EMAIL_THROTTLE = 5.minutes

/**
 * Přidání rezervací bez účtu k účtu podle shody kontaktního e-mailu.
 *
 * Na rozdíl od [AuthenticatedReservationService.claimReservation], kde je laťkou znalost
 * UUID rezervace, tady se páruje jen podle adresy — a tu registrace nijak neověřuje.
 * Proto mezi „našli jsme ti rezervace“ a jejich připsáním stojí odkaz odeslaný na tu
 * adresu: jednorázové ověření schránky navázané právě na tuhle akci.
 *
 * Identita chodí parametrem, ne z JWT — volající si ji přiloží ze svého seamu, takže
 * tahle třída jde v testech instancovat rovnou nad in-memory repozitáři.
 */
class ReservationClaimService(
    private val reservationRepository: ReservationRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val userRepository: UserRepository,
    private val claimTokenRepository: ReservationClaimTokenRepository,
    private val emailService: EmailService,
    private val appBaseUrl: String,
    private val audit: AuditService,
) {

    private val logger = KtorSimpleLogger(this::class.jvmName)

    /**
     * Kolik rezervací bez účtu čeká na e-mail uživatele.
     *
     * Ven jde **jen počet**. Kdo se právě zaregistroval, vlastnictví adresy ještě
     * neprokázal — názvy akcí a termíny cizích rezervací by se mu sdělovat neměly.
     * Výpis je až v mailu, který chodí majiteli schránky.
     */
    suspend fun countClaimable(userId: Uuid, now: Instant): Int =
        claimableFor(userId, now).size

    /**
     * Pošle na adresu účtu potvrzovací odkaz. Vrací počet rezervací, kterých se týká.
     *
     * Token vzniká před odesláním (opačné pořadí by poslalo odkaz na něco, co v databázi
     * ještě není) a nová žádost zneplatní starou, ať ve schránce neleží dva funkční odkazy.
     */
    suspend fun requestClaim(userId: Uuid, now: Instant): Either<ReservationError.RequestClaim, Int> = either {
        val user = ensureNotNull(userRepository.findById(userId)) { ReservationError.NothingToClaim }
        val claimable = claimableFor(userId, now)
        ensure(claimable.isNotEmpty()) { ReservationError.NothingToClaim }

        // Opakované kliknutí na tlačítko nemá znamenat další mail; odkaz už je na cestě.
        val recent = claimTokenRepository.findLatestUnusedFor(userId)
        if (recent != null && recent.expiresAt > now && now - recent.createdAt < CLAIM_EMAIL_THROTTLE) {
            return@either claimable.size
        }

        claimTokenRepository.deleteUnusedFor(userId)
        val token = Uuid.random().toString()
        claimTokenRepository.save(
            ReservationClaimToken(
                token = token,
                userId = userId,
                email = user.email.trim().lowercase(),
                createdAt = now,
                expiresAt = now + CLAIM_LINK_VALIDITY,
            )
        )

        val items = listItemsFor(claimable, now)
        emailService.sendReservationClaimEmail(
            toEmail = user.email,
            reservations = items,
            claimToken = token,
            // Účet jazyk nedrží, rezervace ano — mail tedy jde v jazyce, ve kterém člověk rezervoval.
            locale = claimable.first().locale,
        ).onLeft { error ->
            // Odkaz, ke kterému nedorazil mail, je jen odpad a blokoval by škrcení.
            claimTokenRepository.deleteUnusedFor(userId)
            raise(ReservationError.ClaimEmailSendFailed(error.localizedMessage))
        }

        logger.info("Reservation claim link sent user=$userId count=${claimable.size}")
        claimable.size
    }

    /**
     * Uplatní odkaz z mailu.
     *
     * Podmínky se ověřují znovu — mezi odesláním a klikem mohla rezervace doběhnout nebo
     * ji mohl zabrat někdo jiný. Takové se tiše přeskočí; částečný úspěch je pořád úspěch.
     */
    suspend fun confirmClaim(token: String, now: Instant): Either<ReservationError.ConfirmClaim, ClaimResult> = either {
        val row = ensureNotNull(claimTokenRepository.findByToken(token)) { ReservationError.ClaimLinkInvalid }

        // Obnovení potvrzovací stránky je běžná věc a nemá vypadat jako chyba.
        if (row.usedAt != null) return@either ClaimResult(claimed = row.linkedCount ?: 0, alreadyDone = true)
        ensure(row.expiresAt > now) { ReservationError.ClaimLinkExpired }

        val user = ensureNotNull(userRepository.findById(row.userId)) { ReservationError.ClaimLinkInvalid }
        // Mezi vydáním a uplatněním si mohl někdo e-mail účtu přepsat; odkaz pak platit nesmí,
        // jinak by sáhl na rezervace vedené na adresu, kterou nikdo neověřil.
        ensure(user.email.trim().lowercase() == row.email) { ReservationError.ClaimLinkInvalid }

        // Zámek dřív než zápisy: dvě souběžně otevřené záložky nesmí připisovat obě.
        ensure(claimTokenRepository.markUsed(token, now, linkedCount = 0)) {
            ReservationError.ClaimLinkInvalid
        }

        val candidates = claimableFor(row.userId, now)
        var claimed = 0
        var skipped = 0
        candidates.forEach { reservation ->
            val target = targetOf(reservation) ?: run { skipped++; return@forEach }
            if (!reservationRepository.linkToUser(reservation.id, row.userId)) {
                skipped++
                return@forEach
            }
            claimed++
            val subject = auditSubjectFor(target, reservation.id)
            audit.record(
                type = AuditEventType.RESERVATION_CLAIMED,
                subjectLabel = reservation.contactName,
                seriesId = subject.seriesId,
                instanceId = subject.instanceId,
                reservationId = reservation.id,
                detail = "potvrzený odkaz z e-mailu, shoda e-mailu ${reservation.contactEmail}",
            )
        }

        // Ať druhé otevření odkazu ukáže skutečné číslo, ne nulu.
        claimTokenRepository.updateLinkedCount(token, claimed)
        logger.info("Reservation claim confirmed user=${row.userId} claimed=$claimed skipped=$skipped")

        ClaimResult(claimed = claimed, skipped = skipped)
    }

    /**
     * Aktivní rezervace bez účtu na e-mail uživatele, jejichž akce nebo kurz ještě běží.
     *
     * Dotaz do databáze porovnává adresu bez ohledu na velikost písmen, ale netrimuje ji —
     * uložené adresy jsou tak, jak je člověk napsal. Proto se výsledek ještě profiltruje
     * přes [isClaimableBy], která trimuje obě strany.
     */
    private suspend fun claimableFor(userId: Uuid, now: Instant): List<Reservation> {
        val user = userRepository.findById(userId) ?: return emptyList()
        return reservationRepository.findUnclaimedByEmail(user.email.trim().lowercase())
            .filter { it.isClaimableBy(userId, user.email) }
            .filter { reservation -> targetOf(reservation)?.isStillRunning(now) == true }
    }

    private suspend fun listItemsFor(reservations: List<Reservation>, now: Instant): List<MyReservationListItem> =
        runningReservationListItems(reservations, eventInstanceRepository, eventSeriesRepository, now)

    private suspend fun targetOf(reservation: Reservation): ReservationTarget? =
        when (val ref = reservation.reference) {
            is Reference.Instance -> eventInstanceRepository.get(ref.id)?.let { ReservationTarget.Instance(it) }
            is Reference.Series -> eventSeriesRepository.get(ref.id)?.let { ReservationTarget.Series(it) }
        }
}
