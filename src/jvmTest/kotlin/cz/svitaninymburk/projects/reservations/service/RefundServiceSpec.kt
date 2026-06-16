package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.NewWallet
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.wallet.WalletTransaction
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class RefundServiceSpec {

    private fun walletRepo() = InMemoryWalletRepository()

    private fun refundService(repo: InMemoryWalletRepository) = RefundService(
        walletService = WalletService(repo),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        ),
    )

    private fun reservation(
        paidAmount: Double = 0.0,
        walletDeductedAmount: Double = 0.0,
    ) = Reservation(
        id = Uuid.random(),
        reference = Reference.Instance(Uuid.random()),
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        seatCount = 1,
        totalPrice = paidAmount,
        paidAmount = paidAmount,
        walletDeductedAmount = walletDeductedAmount,
        status = Reservation.Status.CONFIRMED,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
    )

    @Test
    fun `refundFixedAmount credits the amount and returns outcome`() = runBlocking {
        val repo = walletRepo()
        val wallet = repo.create(NewWallet(code = "SVIT-AAAA-AAAA", ownerEmail = "jan@test.com"))
        val res = reservation(paidAmount = 200.0)

        val outcome = refundService(repo).refundFixedAmount(
            wallet, res, 50.0, WalletTransactionReason.LESSON_OPT_OUT_REFUND
        )

        assertEquals("SVIT-AAAA-AAAA", outcome?.walletCode)
        assertEquals(50.0, outcome?.creditedAmount)
        assertEquals(50.0, repo.findById(wallet.id)?.balance)
    }

    @Test
    fun `refundFixedAmount returns null for non-positive amount`() = runBlocking {
        val repo = walletRepo()
        val wallet = repo.create(NewWallet(code = "SVIT-AAAA-AAAA", ownerEmail = "jan@test.com"))
        val outcome = refundService(repo).refundFixedAmount(
            wallet, reservation(), 0.0, WalletTransactionReason.LESSON_OPT_OUT_REFUND
        )
        assertNull(outcome)
        assertEquals(0.0, repo.findById(wallet.id)?.balance)
    }

    @Test
    fun `refundWholeReservation credits full paidAmount when no wallet was used`() = runBlocking {
        val repo = walletRepo()
        val wallet = repo.create(NewWallet(code = "SVIT-BBBB-BBBB", ownerEmail = "jan@test.com"))
        val res = reservation(paidAmount = 300.0, walletDeductedAmount = 0.0)

        val outcome = refundService(repo).refundWholeReservation(wallet, res)

        assertEquals(300.0, outcome?.creditedAmount)
        assertEquals(300.0, repo.findById(wallet.id)?.balance)
    }

    @Test
    fun `refundWholeReservation reverses wallet debit then refunds cash remainder`() = runBlocking {
        val repo = walletRepo()
        val wallet = repo.create(NewWallet(code = "SVIT-CCCC-CCCC", ownerEmail = "jan@test.com"))
        val res = reservation(paidAmount = 300.0, walletDeductedAmount = 100.0)

        val outcome = refundService(repo).refundWholeReservation(wallet, res)

        assertEquals(300.0, outcome?.creditedAmount)
        assertEquals(300.0, repo.findById(wallet.id)?.balance)

        val transactions = repo.getTransactions(wallet.id)
        val reversals = transactions.filter { it.reason == WalletTransactionReason.RESERVATION_DEBIT_REVERSAL }
        val refunds = transactions.filter { it.reason == WalletTransactionReason.CANCELLATION_REFUND }
        assertEquals(1, reversals.size)
        assertEquals(100.0, reversals.single().amount)
        assertEquals(1, refunds.size)
        assertEquals(200.0, refunds.single().amount)
    }

    @Test
    fun `refundWholeReservation reverses full wallet debit with no cash remainder`() = runBlocking {
        val repo = walletRepo()
        val wallet = repo.create(NewWallet(code = "SVIT-EEEE-EEEE", ownerEmail = "jan@test.com"))
        val res = reservation(paidAmount = 100.0, walletDeductedAmount = 100.0)

        val outcome = refundService(repo).refundWholeReservation(wallet, res)

        assertEquals(100.0, outcome?.creditedAmount)
        assertEquals(100.0, repo.findById(wallet.id)?.balance)

        val transactions = repo.getTransactions(wallet.id)
        assertEquals(1, transactions.size)
        val reversal: WalletTransaction = transactions.single()
        assertEquals(WalletTransactionReason.RESERVATION_DEBIT_REVERSAL, reversal.reason)
        assertEquals(100.0, reversal.amount)
        assertTrue(transactions.none { it.reason == WalletTransactionReason.CANCELLATION_REFUND })
    }

    @Test
    fun `refundWholeReservation returns null when nothing was paid`() = runBlocking {
        val repo = walletRepo()
        val wallet = repo.create(NewWallet(code = "SVIT-DDDD-DDDD", ownerEmail = "jan@test.com"))
        val outcome = refundService(repo).refundWholeReservation(wallet, reservation(paidAmount = 0.0))
        assertNull(outcome)
        assertEquals(0.0, repo.findById(wallet.id)?.balance)
    }
}
