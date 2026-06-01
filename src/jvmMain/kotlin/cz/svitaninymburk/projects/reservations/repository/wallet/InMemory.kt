package cz.svitaninymburk.projects.reservations.repository.wallet

import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletTransaction
import kotlin.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class InMemoryWalletRepository : WalletRepository {
    private val wallets = ConcurrentHashMap<Uuid, Wallet>()
    private val transactions = ConcurrentHashMap<Uuid, WalletTransaction>()

    override suspend fun create(wallet: NewWallet): Wallet {
        val w = Wallet(
            id = Uuid.random(),
            code = wallet.code,
            ownerEmail = wallet.ownerEmail,
            registeredUserId = wallet.registeredUserId,
            balance = 0.0,
            createdAt = Clock.System.now(),
        )
        wallets[w.id] = w
        return w
    }

    override suspend fun findById(id: Uuid): Wallet? = wallets[id]
    override suspend fun findByCode(code: String) = wallets.values.find { it.code == code }
    override suspend fun findByRegisteredUserId(userId: Uuid) = wallets.values.find { it.registeredUserId == userId }
    override suspend fun findByEmail(email: String) = wallets.values.find { it.ownerEmail == email }

    override suspend fun updateBalance(walletId: Uuid, newBalance: Double): Wallet {
        val updated = wallets[walletId]!!.copy(balance = newBalance)
        wallets[walletId] = updated
        return updated
    }

    override suspend fun addToBalance(walletId: Uuid, delta: Double): Wallet {
        val current = wallets[walletId]?.balance ?: 0.0
        val updated = wallets[walletId]!!.copy(balance = (current + delta).coerceAtLeast(0.0))
        wallets[walletId] = updated
        return updated
    }

    override suspend fun insertTransaction(tx: NewWalletTransaction): WalletTransaction {
        val t = WalletTransaction(
            id = Uuid.random(),
            walletId = tx.walletId,
            amount = tx.amount,
            reason = tx.reason,
            reservationId = tx.reservationId,
            note = tx.note,
            createdAt = Clock.System.now(),
        )
        transactions[t.id] = t
        return t
    }

    override suspend fun getTransactions(walletId: Uuid) =
        transactions.values.filter { it.walletId == walletId }

    override suspend fun findAll(page: Int, pageSize: Int) =
        wallets.values.toList().drop(page * pageSize).take(pageSize)

    override suspend fun countAll() = wallets.size.toLong()
    override suspend fun findAllWithPositiveBalance() = wallets.values.filter { it.balance > 0.0 }
    override suspend fun isCodeUnique(code: String) = wallets.values.none { it.code == code }
}
