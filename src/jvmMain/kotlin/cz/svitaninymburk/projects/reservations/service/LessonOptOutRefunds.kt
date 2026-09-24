package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.reflect.jvm.jvmName

/**
 * Dodatečná vratka za omluvenky, které přišly dřív než peníze.
 *
 * Omluvenka z nezaplaceného kurzu kredit nedostane — z nezaplacené rezervace by
 * vznikal kredit z ničeho (viz `cancelReservation`). Když ale platba dorazí
 * potom, host by bez tohohle o kredit za včas omluvenou lekci přišel potichu.
 * Proto se po každém zvýšení `paidAmount` dorovná, co omluvenkám chybí.
 *
 * Idempotentní — dorovnává se jen rozdíl proti [SeriesLessonOptOut.refundedAmount],
 * takže opakované volání nic nezdvojí. Stejné stropy jako u omluvenky samotné:
 * nejvýš kredit za lekci ([lessonCreditFor]) a dohromady nejvýš zaplacená částka.
 */
class LessonOptOutRefunds(
    private val seriesLessonOptOutRepository: SeriesLessonOptOutRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val walletService: WalletService,
    private val refundService: RefundService,
) {
    private val logger = KtorSimpleLogger(this::class.jvmName)

    /** Vrací, kolik se dodatečně připsalo (0.0, když nic). */
    suspend fun settleAfterPayment(reservation: Reservation): Double {
        if (reservation.status == Reservation.Status.CANCELLED) return 0.0
        val seriesId = (reservation.reference as? Reference.Series)?.id ?: return 0.0
        if (reservation.paidAmount <= 0.0) return 0.0

        val perLesson = lessonCreditFor(reservation, eventSeriesRepository.get(seriesId))
        if (perLesson <= 0.0) return 0.0

        // Pozdní omluvenka nic nedostává — příznak se vyhodnotil v čase odhlášení,
        // přepočet teď by dal jiný výsledek. Omluvenka s neznámou částkou (null)
        // se nedoplácí: mohla být proplacená a ze součtu to nejde poznat.
        val short = seriesLessonOptOutRepository.findByReservation(reservation.id)
            .filter { !it.isLateCancellation }
            .mapNotNull { optOut -> optOut.refundedAmount?.let { optOut to (perLesson - it) } }
            .filter { (_, missing) -> missing > 0.0 }
            .sortedBy { (optOut, _) -> optOut.optedOutAt }
        if (short.isEmpty()) return 0.0

        var room = reservation.paidAmount - walletService.refundedForLessonOptOuts(reservation.id)
        val topUps = short.mapNotNull { (optOut, missing) ->
            val add = minOf(missing, room)
            if (add <= 0.0) return@mapNotNull null
            room -= add
            optOut to add
        }
        val total = topUps.sumOf { it.second }
        if (total <= 0.0) return 0.0

        // Jedno připsání a jeden mail za všechny omluvenky — host bez účtu se kód
        // peněženky dozví jen z toho mailu.
        refundService.refundFixedAmount(
            walletFor(reservation), reservation, total,
            WalletTransactionReason.LESSON_OPT_OUT_REFUND,
            detail = "dodatečně po zaplacení za ${topUps.size} omluven${if (topUps.size == 1) "ku" else "ky"}",
        )
        topUps.forEach { (optOut, add) ->
            seriesLessonOptOutRepository.updateRefundedAmount(optOut.id, (optOut.refundedAmount ?: 0.0) + add)
        }
        logger.info("Dodatečná vratka za omluvenky reservation=${reservation.id} amount=$total count=${topUps.size}")
        return total
    }

    /**
     * Stejná peněženka, kam chodí ostatní vratky omluvenek — u hosta podle e-mailu,
     * aby se kredit netříštil do nových peněženek s novými kódy.
     */
    private suspend fun walletFor(reservation: Reservation): Wallet {
        val registeredUserId = reservation.registeredUserId
        return if (registeredUserId != null) {
            walletService.findOrCreateForRegisteredUser(registeredUserId, reservation.contactEmail)
        } else {
            // Bez kódu nemá co nesedět na e-mail, takže je to vždy Right.
            walletService.resolveAnonymousWallet(null, reservation.contactEmail, force = true)
                .getOrNull() ?: error("resolveAnonymousWallet(null) must return a wallet")
        }
    }
}
