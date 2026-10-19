package cz.svitaninymburk.projects.reservations.repository.claim

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Jednorázový odkaz, kterým si uživatel potvrdí, že mu patří schránka, na kterou
 * jsou vedené rezervace bez účtu.
 *
 * [email] je otisk adresy účtu v okamžiku vyžádání, ne odkaz do `users` — kdyby si
 * uživatel mezitím e-mail změnil, nesmí mu ten samý odkaz připsat rezervace vedené
 * na jinou adresu.
 */
data class ReservationClaimToken(
    val token: String,
    val userId: Uuid,
    val email: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val usedAt: Instant? = null,
    /** Kolik rezervací odkaz nakonec připsal. Dokud není uplatněný, je `null`. */
    val linkedCount: Int? = null,
)

interface ReservationClaimTokenRepository {
    suspend fun save(token: ReservationClaimToken)
    suspend fun findByToken(token: String): ReservationClaimToken?

    /** Nejnovější dosud neuplatněný odkaz uživatele — kvůli škrcení opakovaných odeslání. */
    suspend fun findLatestUnusedFor(userId: Uuid): ReservationClaimToken?

    /**
     * Označí odkaz za uplatněný a uloží, kolik rezervací připsal. Úzký
     * `UPDATE … WHERE used_at IS NULL` je zároveň zámek: při dvou souběžných otevřeních
     * odkazu uspěje jen jedno a vrátí `true`. Uložený počet pak umí druhé otevření
     * zopakovat jako výsledek místo chyby.
     */
    suspend fun markUsed(token: String, now: Instant, linkedCount: Int): Boolean

    /** Dopíše k už uplatněnému odkazu, kolik rezervací připsal. */
    suspend fun updateLinkedCount(token: String, linkedCount: Int)

    /** Nové vyžádání zneplatní starý odkaz, ať v schránce neleží dva funkční. */
    suspend fun deleteUnusedFor(userId: Uuid)
}
