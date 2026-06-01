package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.service.WalletService
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WalletServiceSpec {

    private fun service() = WalletService(InMemoryWalletRepository())

    @Test
    fun `generateUniqueCode produces SVIT prefix with correct format`() = runBlocking {
        val code = service().generateUniqueCode()
        assertTrue(code.startsWith("SVIT-"), "Code should start with SVIT-")
        assertEquals(14, code.length, "Expected SVIT-XXXX-XXXX (14 chars)")
    }

    @Test
    fun `findOrCreateForRegisteredUser creates wallet on first call`() = runBlocking {
        val svc = service()
        val userId = Uuid.random()
        val wallet = svc.findOrCreateForRegisteredUser(userId, "user@test.com")
        assertEquals("user@test.com", wallet.ownerEmail)
        assertEquals(userId, wallet.registeredUserId)
        assertEquals(0.0, wallet.balance)
    }

    @Test
    fun `findOrCreateForRegisteredUser returns same wallet on second call`() = runBlocking {
        val svc = service()
        val userId = Uuid.random()
        val first = svc.findOrCreateForRegisteredUser(userId, "user@test.com")
        val second = svc.findOrCreateForRegisteredUser(userId, "user@test.com")
        assertEquals(first.id, second.id)
    }

    @Test
    fun `credit increases wallet balance and records transaction`() = runBlocking {
        val svc = service()
        val wallet = svc.findOrCreateForRegisteredUser(Uuid.random(), "user@test.com")
        val updated = svc.credit(wallet.id, 200.0, WalletTransactionReason.CANCELLATION_REFUND)
        assertEquals(200.0, updated.balance)
        val txs = svc.getTransactions(wallet.id)
        assertEquals(1, txs.size)
        assertEquals(200.0, txs[0].amount)
        assertEquals(WalletTransactionReason.CANCELLATION_REFUND, txs[0].reason)
    }

    @Test
    fun `debit decreases wallet balance and records negative transaction`() = runBlocking {
        val svc = service()
        val wallet = svc.findOrCreateForRegisteredUser(Uuid.random(), "u@t.com")
        svc.credit(wallet.id, 300.0, WalletTransactionReason.ADMIN_CREDIT)
        val after = svc.debit(wallet.id, 100.0, WalletTransactionReason.RESERVATION_DEBIT)
        assertEquals(200.0, after.balance)
        val txs = svc.getTransactions(wallet.id)
        val debitTx = txs.find { it.reason == WalletTransactionReason.RESERVATION_DEBIT }
        assertNotNull(debitTx)
        assertEquals(-100.0, debitTx.amount)
    }

    @Test
    fun `debit clamps to current balance when amount exceeds balance`() = runBlocking {
        val svc = service()
        val wallet = svc.findOrCreateForRegisteredUser(Uuid.random(), "u@t.com")
        svc.credit(wallet.id, 50.0, WalletTransactionReason.ADMIN_CREDIT)
        val after = svc.debit(wallet.id, 200.0, WalletTransactionReason.RESERVATION_DEBIT)
        assertEquals(0.0, after.balance)
    }

    @Test
    fun `resolveAnonymousWallet creates new wallet when code is null`() = runBlocking {
        val svc = service()
        val result = svc.resolveAnonymousWallet(null, "anon@test.com", false)
        assertTrue(result.isRight())
        result.onRight { assertEquals("anon@test.com", it.ownerEmail) }
        Unit
    }

    @Test
    fun `resolveAnonymousWallet returns WalletEmailMismatch for wrong email and force=false`() = runBlocking {
        val svc = service()
        val wallet = svc.findOrCreateForRegisteredUser(Uuid.random(), "owner@test.com")
        val result = svc.resolveAnonymousWallet(wallet.code, "other@test.com", force = false)
        assertTrue(result.isLeft())
        result.onLeft { assertEquals(ReservationError.WalletEmailMismatch, it) }
        Unit
    }

    @Test
    fun `resolveAnonymousWallet proceeds when force=true despite email mismatch`() = runBlocking {
        val svc = service()
        val wallet = svc.findOrCreateForRegisteredUser(Uuid.random(), "owner@test.com")
        val result = svc.resolveAnonymousWallet(wallet.code, "other@test.com", force = true)
        assertTrue(result.isRight())
    }

    @Test
    fun `performSeasonReset zeros all wallets with positive balance`() = runBlocking {
        val svc = service()
        val w1 = svc.findOrCreateForRegisteredUser(Uuid.random(), "a@test.com")
        val w2 = svc.findOrCreateForRegisteredUser(Uuid.random(), "b@test.com")
        svc.credit(w1.id, 100.0, WalletTransactionReason.ADMIN_CREDIT)
        svc.credit(w2.id, 200.0, WalletTransactionReason.ADMIN_CREDIT)
        val count = svc.performSeasonReset()
        assertEquals(2, count)
        assertEquals(0.0, svc.getWalletInfo(w1.code)?.balance)
        assertEquals(0.0, svc.getWalletInfo(w2.code)?.balance)
    }

    @Test
    fun `performSeasonReset records SEASON_RESET transaction for each wallet`() = runBlocking {
        val svc = service()
        val wallet = svc.findOrCreateForRegisteredUser(Uuid.random(), "c@test.com")
        svc.credit(wallet.id, 150.0, WalletTransactionReason.ADMIN_CREDIT)
        svc.performSeasonReset()
        val txs = svc.getTransactions(wallet.id)
        val resetTx = txs.find { it.reason == WalletTransactionReason.SEASON_RESET }
        assertNotNull(resetTx)
        assertEquals(-150.0, resetTx.amount)
    }
}
