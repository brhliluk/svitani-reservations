package cz.svitaninymburk.projects.reservations.service

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
) {
    private val logger = KtorSimpleLogger(this::class.jvmName)

    /**
     * Refund the whole reservation's paid amount: first reverse any wallet debit
     * (RESERVATION_DEBIT_REVERSAL), then refund the cash remainder
     * (CANCELLATION_REFUND). Returns null when nothing was paid.
     */
    suspend fun refundWholeReservation(wallet: Wallet, reservation: Reservation): RefundOutcome? {
        val paidAmount = reservation.paidAmount
        if (paidAmount <= 0.0) return null

        var updatedWallet = wallet
        if (reservation.walletDeductedAmount > 0.0) {
            updatedWallet = walletService.credit(
                wallet.id, reservation.walletDeductedAmount,
                WalletTransactionReason.RESERVATION_DEBIT_REVERSAL, reservation.id
            )
            val cashPaid = paidAmount - reservation.walletDeductedAmount
            if (cashPaid > 0.0) {
                updatedWallet = walletService.credit(
                    wallet.id, cashPaid, WalletTransactionReason.CANCELLATION_REFUND, reservation.id
                )
            }
        } else {
            updatedWallet = walletService.credit(
                wallet.id, paidAmount, WalletTransactionReason.CANCELLATION_REFUND, reservation.id
            )
        }

        notifyCredited(updatedWallet, paidAmount, reservation)
        return RefundOutcome(updatedWallet.code, paidAmount)
    }

    /** Credit a fixed amount under [reason] (e.g. a single cancelled lesson). Returns null for non-positive amounts. */
    suspend fun refundFixedAmount(
        wallet: Wallet,
        reservation: Reservation,
        amount: Double,
        reason: WalletTransactionReason,
    ): RefundOutcome? {
        if (amount <= 0.0) return null
        val updatedWallet = walletService.credit(wallet.id, amount, reason, reservation.id)
        notifyCredited(updatedWallet, amount, reservation)
        return RefundOutcome(updatedWallet.code, amount)
    }

    private suspend fun notifyCredited(wallet: Wallet, creditedAmount: Double, reservation: Reservation) {
        val settings = appSettingsProvider.current
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
