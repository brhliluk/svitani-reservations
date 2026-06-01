package cz.svitaninymburk.projects.reservations.wallet

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
enum class WalletTransactionReason {
    CANCELLATION_REFUND,
    LESSON_OPT_OUT_REFUND,
    RESERVATION_DEBIT,
    RESERVATION_DEBIT_REVERSAL,
    ADMIN_CREDIT,
    ADMIN_DEBIT,
    SEASON_RESET,
}

@Serializable
data class WalletTransaction(
    val id: Uuid,
    val walletId: Uuid,
    val amount: Double,
    val reason: WalletTransactionReason,
    val reservationId: Uuid? = null,
    val note: String? = null,
    val createdAt: Instant,
)
