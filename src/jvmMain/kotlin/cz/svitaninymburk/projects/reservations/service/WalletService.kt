package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import cz.svitaninymburk.projects.reservations.error.WalletError
import cz.svitaninymburk.projects.reservations.repository.wallet.NewWallet
import cz.svitaninymburk.projects.reservations.repository.wallet.NewWalletTransaction
import cz.svitaninymburk.projects.reservations.repository.wallet.WalletRepository
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletTransaction
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import cz.svitaninymburk.projects.reservations.wallet.WalletsPage
import kotlin.uuid.Uuid

class WalletService(private val repo: WalletRepository) {

    /** Generate a unique human-readable wallet code: SVIT-XXXX-XXXX */
    suspend fun generateUniqueCode(): String {
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        var code: String
        do {
            fun group() = (1..4).map { chars.random() }.joinToString("")
            code = "SVIT-${group()}-${group()}"
        } while (!repo.isCodeUnique(code))
        return code
    }

    /** Find or create a wallet for a registered user. */
    suspend fun findOrCreateForRegisteredUser(userId: Uuid, email: String): Wallet {
        return repo.findByRegisteredUserId(userId)
            ?: repo.create(NewWallet(code = generateUniqueCode(), ownerEmail = email, registeredUserId = userId))
    }

    /**
     * Resolve a wallet for anonymous user cancellation.
     * Returns WalletEmailMismatch if code belongs to different email and force=false.
     * Creates a new wallet if code is null.
     */
    suspend fun resolveAnonymousWallet(
        code: String?,
        contactEmail: String,
        force: Boolean,
    ): Either<WalletError.ResolveAnonymous, Wallet> {
        if (code == null) {
            val newWallet = repo.create(NewWallet(code = generateUniqueCode(), ownerEmail = contactEmail))
            return newWallet.right()
        }
        val wallet = repo.findByCode(code)
            ?: return WalletError.NotFound.left()
        if (!force && !wallet.ownerEmail.equals(contactEmail, ignoreCase = true)) {
            return WalletError.EmailMismatch.left()
        }
        return wallet.right()
    }

    /**
     * Validate a wallet code for use during reservation creation.
     * Returns WalletNotFound or WalletEmpty if invalid.
     */
    suspend fun validateForReservation(code: String): Either<WalletError.ValidateForReservation, Wallet> {
        val wallet = repo.findByCode(code) ?: return WalletError.NotFound.left()
        if (wallet.balance <= 0.0) return WalletError.Empty.left()
        return wallet.right()
    }

    /** Credit the wallet and record the transaction. Returns the updated wallet. */
    suspend fun credit(
        walletId: Uuid,
        amount: Double,
        reason: WalletTransactionReason,
        reservationId: Uuid? = null,
        note: String? = null,
    ): Wallet {
        val updated = repo.addToBalance(walletId, amount)
        repo.insertTransaction(NewWalletTransaction(walletId, amount, reason, reservationId, note))
        return updated
    }

    /** Debit the wallet (amount clamped to current balance). Returns the updated wallet. */
    suspend fun debit(
        walletId: Uuid,
        amount: Double,
        reason: WalletTransactionReason,
        reservationId: Uuid? = null,
    ): Wallet {
        val current = getCurrentBalance(walletId)
        val deduct = minOf(amount, current)
        val updated = repo.addToBalance(walletId, -deduct)
        repo.insertTransaction(NewWalletTransaction(walletId, -deduct, reason, reservationId))
        return updated
    }

    /** Zero all wallets with positive balance. Returns count zeroed. */
    suspend fun performSeasonReset(): Int {
        val wallets = repo.findAllWithPositiveBalance()
        wallets.forEach { w ->
            repo.insertTransaction(NewWalletTransaction(w.id, -w.balance, WalletTransactionReason.SEASON_RESET))
            repo.updateBalance(w.id, 0.0)
        }
        return wallets.size
    }

    suspend fun getWalletsForResetWarning(): List<Wallet> = repo.findAllWithPositiveBalance()

    suspend fun getWalletInfo(code: String): Wallet? = repo.findByCode(code)

    suspend fun getAll(page: Int, pageSize: Int): WalletsPage = WalletsPage(
        items = repo.findAll(page, pageSize),
        totalCount = repo.countAll(),
        page = page,
        pageSize = pageSize,
    )

    suspend fun adminCredit(walletId: Uuid, amount: Double, note: String): Wallet =
        credit(walletId, amount, WalletTransactionReason.ADMIN_CREDIT, note = note)

    suspend fun adminDebit(walletId: Uuid, amount: Double, note: String): Wallet {
        val current = getCurrentBalance(walletId)
        val deduct = minOf(amount, current)
        repo.insertTransaction(NewWalletTransaction(walletId, -deduct, WalletTransactionReason.ADMIN_DEBIT, note = note))
        return repo.addToBalance(walletId, -deduct)
    }

    suspend fun getTransactions(walletId: Uuid): List<WalletTransaction> = repo.getTransactions(walletId)

    private suspend fun getCurrentBalance(walletId: Uuid): Double =
        repo.findById(walletId)?.balance ?: 0.0
}
