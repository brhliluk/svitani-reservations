package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.util.captureEmailError
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.reflect.jvm.jvmName

/** Result of a refund credited to a wallet. */
data class RefundOutcome(val walletCode: String, val creditedAmount: Double)

/**
 * Credits reservation refunds to wallets and notifies the customer.
 * Callers decide whether and how much to refund; this service performs the
 * wallet credit(s) and sends the "wallet credited" email.
 */
class RefundService(
    private val walletService: WalletService,
    private val walletEmailService: WalletEmailService,
    private val appSettingsProvider: AppSettingsProvider,
    private val audit: AuditService = AuditService(InMemoryAuditRepository()),
    /**
     * Viz stejný parametr u [ReservationService]. Podstatné hlavně u hromadných
     * stornen: rušení kurzu volá vracení kreditu za každého zaplaceného účastníka
     * a každé z nich odsud pošle mail.
     */
    private val emailDispatcher: EmailDispatcher = InlineEmailDispatcher,
) {
    private val logger = KtorSimpleLogger(this::class.jvmName)

    /**
     * Kolik by storno celé rezervace ještě vrátilo: zaplaceno mínus to, co už
     * odešlo za omluvenky a adminem zrušené lekce (obojí pod LESSON_OPT_OUT_REFUND,
     * součet už je po případném stržení při vzetí omluvenky zpět).
     */
    suspend fun refundableForWholeReservation(reservation: Reservation): Double =
        (reservation.paidAmount - walletService.refundedForLessonOptOuts(reservation.id)).coerceAtLeast(0.0)

    /**
     * Vrátí z rezervace to, co z ní ještě nebylo vráceno ([refundableForWholeReservation]):
     * nejdřív zpět odpočet z peněženky (RESERVATION_DEBIT_REVERSAL), zbytek jako
     * CANCELLATION_REFUND. Bez odečtu kreditu za lekce by omluvenka a následné
     * storno vyplatily tutéž lekci dvakrát. Returns null when there's nothing to refund.
     */
    suspend fun refundWholeReservation(wallet: Wallet, reservation: Reservation): RefundOutcome? {
        val refundable = refundableForWholeReservation(reservation)
        if (refundable <= 0.0) return null
        val alreadyRefunded = reservation.paidAmount - refundable

        var updatedWallet = wallet
        val reversal = minOf(reservation.walletDeductedAmount, refundable)
        if (reversal > 0.0) {
            updatedWallet = walletService.credit(
                wallet.id, reversal,
                WalletTransactionReason.RESERVATION_DEBIT_REVERSAL, reservation.id
            )
        }
        val cash = refundable - reversal
        if (cash > 0.0) {
            updatedWallet = walletService.credit(
                wallet.id, cash, WalletTransactionReason.CANCELLATION_REFUND, reservation.id
            )
        }

        // Jeden záznam na jedno storno. Rozpad na vrácení odpočtu z peněženky
        // a doplatku v hotovosti je účetní detail — v historii by ze dvou řádků
        // vypadalo, že se vracelo dvakrát.
        audit.record(
            type = AuditEventType.PAYMENT_REFUNDED,
            subjectLabel = reservation.contactName,
            reservationId = reservation.id,
            walletCode = updatedWallet.code,
            amount = refundable,
            // Kód peněženky se v UI vykresluje zvlášť jako odkaz, do textu nepatří.
            detail = listOfNotNull(
                reversal.takeIf { it > 0.0 }?.let { "z toho ${it.toInt()} Kč zpět z kreditu" },
                alreadyRefunded.takeIf { it > 0.0 }?.let { "${it.toInt()} Kč už vráceno za lekce" },
            ).joinToString(", ").ifEmpty { null },
        )

        notifyCredited(updatedWallet, refundable, reservation)
        return RefundOutcome(updatedWallet.code, refundable)
    }

    /** Credit a fixed amount under [reason] (e.g. a single cancelled lesson). Returns null for non-positive amounts. */
    suspend fun refundFixedAmount(
        wallet: Wallet,
        reservation: Reservation,
        amount: Double,
        reason: WalletTransactionReason,
        detail: String = reason.name,
    ): RefundOutcome? {
        if (amount <= 0.0) return null
        val updatedWallet = walletService.credit(wallet.id, amount, reason, reservation.id)

        audit.record(
            type = AuditEventType.PAYMENT_REFUNDED,
            subjectLabel = reservation.contactName,
            reservationId = reservation.id,
            walletCode = updatedWallet.code,
            amount = amount,
            detail = detail,
        )

        notifyCredited(updatedWallet, amount, reservation)
        return RefundOutcome(updatedWallet.code, amount)
    }

    private suspend fun notifyCredited(wallet: Wallet, creditedAmount: Double, reservation: Reservation) {
        val settings = appSettingsProvider.current
        emailDispatcher.dispatch {
            walletEmailService.sendWalletCredited(
                toEmail = reservation.contactEmail,
                walletCode = wallet.code,
                creditedAmount = creditedAmount,
                newBalance = wallet.balance,
                resetMonth = settings.seasonResetMonth,
                resetDay = settings.seasonResetDay,
                locale = reservation.locale,
            ).onLeft { captureEmailError(logger, "Failed to send wallet credited email to ${reservation.contactEmail}: $it") }
        }
    }
}
