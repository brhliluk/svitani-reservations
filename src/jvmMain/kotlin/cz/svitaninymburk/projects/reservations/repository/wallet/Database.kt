package cz.svitaninymburk.projects.reservations.repository.wallet

import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.util.dbQuery
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletTransaction
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

object WalletsTable : Table("wallets") {
    val id = uuid("id").autoGenerate()
    val code = varchar("code", 20).uniqueIndex()
    val ownerEmail = varchar("owner_email", 255)
    val registeredUserId = optReference("registered_user_id", UsersTable.id, ReferenceOption.SET_NULL)
    val balance = double("balance").default(0.0)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object WalletTransactionsTable : Table("wallet_transactions") {
    val id = uuid("id").autoGenerate()
    val walletId = reference("wallet_id", WalletsTable.id, ReferenceOption.CASCADE)
    val amount = double("amount")
    val reason = enumerationByName("reason", 40, WalletTransactionReason::class)
    val reservationId = optReference("reservation_id", ReservationsTable.id, ReferenceOption.SET_NULL)
    val note = varchar("note", 500).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toWallet() = Wallet(
    id = this[WalletsTable.id],
    code = this[WalletsTable.code],
    ownerEmail = this[WalletsTable.ownerEmail],
    registeredUserId = this[WalletsTable.registeredUserId],
    balance = this[WalletsTable.balance],
    createdAt = this[WalletsTable.createdAt],
)

fun ResultRow.toWalletTransaction() = WalletTransaction(
    id = this[WalletTransactionsTable.id],
    walletId = this[WalletTransactionsTable.walletId],
    amount = this[WalletTransactionsTable.amount],
    reason = this[WalletTransactionsTable.reason],
    reservationId = this[WalletTransactionsTable.reservationId],
    note = this[WalletTransactionsTable.note],
    createdAt = this[WalletTransactionsTable.createdAt],
)

data class NewWallet(
    val code: String,
    val ownerEmail: String,
    val registeredUserId: Uuid? = null,
)

data class NewWalletTransaction(
    val walletId: Uuid,
    val amount: Double,
    val reason: WalletTransactionReason,
    val reservationId: Uuid? = null,
    val note: String? = null,
)

interface WalletRepository {
    suspend fun create(wallet: NewWallet): Wallet
    suspend fun findById(id: Uuid): Wallet?
    suspend fun findByCode(code: String): Wallet?
    suspend fun findByRegisteredUserId(userId: Uuid): Wallet?
    suspend fun findByEmail(email: String): Wallet?
    suspend fun updateBalance(walletId: Uuid, newBalance: Double): Wallet
    suspend fun addToBalance(walletId: Uuid, delta: Double): Wallet
    suspend fun insertTransaction(tx: NewWalletTransaction): WalletTransaction
    suspend fun getTransactions(walletId: Uuid): List<WalletTransaction>
    suspend fun findAll(page: Int, pageSize: Int): List<Wallet>
    suspend fun countAll(): Long
    suspend fun findAllWithPositiveBalance(): List<Wallet>
    suspend fun isCodeUnique(code: String): Boolean
}

class ExposedWalletRepository : WalletRepository {

    override suspend fun create(wallet: NewWallet): Wallet = dbQuery {
        val now = Clock.System.now()
        WalletsTable.insert {
            it[code] = wallet.code
            it[ownerEmail] = wallet.ownerEmail
            it[registeredUserId] = wallet.registeredUserId
            it[balance] = 0.0
            it[createdAt] = now
        }
        WalletsTable.selectAll().where { WalletsTable.code eq wallet.code }.single().toWallet()
    }

    override suspend fun findById(id: Uuid): Wallet? = dbQuery {
        WalletsTable.selectAll().where { WalletsTable.id eq id }.singleOrNull()?.toWallet()
    }

    override suspend fun findByCode(code: String): Wallet? = dbQuery {
        WalletsTable.selectAll().where { WalletsTable.code eq code }.singleOrNull()?.toWallet()
    }

    override suspend fun findByRegisteredUserId(userId: Uuid): Wallet? = dbQuery {
        WalletsTable.selectAll().where { WalletsTable.registeredUserId eq userId }.singleOrNull()?.toWallet()
    }

    override suspend fun findByEmail(email: String): Wallet? = dbQuery {
        WalletsTable.selectAll().where { WalletsTable.ownerEmail eq email }.singleOrNull()?.toWallet()
    }

    override suspend fun updateBalance(walletId: Uuid, newBalance: Double): Wallet = dbQuery {
        WalletsTable.update({ WalletsTable.id eq walletId }) { it[balance] = newBalance }
        WalletsTable.selectAll().where { WalletsTable.id eq walletId }.single().toWallet()
    }

    override suspend fun addToBalance(walletId: Uuid, delta: Double): Wallet = dbQuery {
        WalletsTable.update({ WalletsTable.id eq walletId }) {
            it[balance] = WalletsTable.balance + delta
        }
        WalletsTable.selectAll().where { WalletsTable.id eq walletId }.single().toWallet()
    }

    override suspend fun insertTransaction(tx: NewWalletTransaction): WalletTransaction = dbQuery {
        val now = Clock.System.now()
        WalletTransactionsTable.insert {
            it[walletId] = tx.walletId
            it[amount] = tx.amount
            it[reason] = tx.reason
            it[reservationId] = tx.reservationId
            it[note] = tx.note
            it[createdAt] = now
        }
        WalletTransactionsTable
            .selectAll()
            .where { WalletTransactionsTable.walletId eq tx.walletId }
            .last()
            .toWalletTransaction()
    }

    override suspend fun getTransactions(walletId: Uuid): List<WalletTransaction> = dbQuery {
        WalletTransactionsTable.selectAll()
            .where { WalletTransactionsTable.walletId eq walletId }
            .map { it.toWalletTransaction() }
    }

    override suspend fun findAll(page: Int, pageSize: Int): List<Wallet> = dbQuery {
        WalletsTable.selectAll()
            .limit(pageSize).offset((page * pageSize).toLong())
            .map { it.toWallet() }
    }

    override suspend fun countAll(): Long = dbQuery {
        WalletsTable.selectAll().count()
    }

    override suspend fun findAllWithPositiveBalance(): List<Wallet> = dbQuery {
        WalletsTable.selectAll()
            .where { WalletsTable.balance greater 0.0 }
            .map { it.toWallet() }
    }

    override suspend fun isCodeUnique(code: String): Boolean = dbQuery {
        WalletsTable.selectAll().where { WalletsTable.code eq code }.count() == 0L
    }
}
