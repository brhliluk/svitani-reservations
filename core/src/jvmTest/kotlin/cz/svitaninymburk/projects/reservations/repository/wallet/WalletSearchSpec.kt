package cz.svitaninymburk.projects.reservations.repository.wallet

import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.service.WalletService
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Hledání v admin přehledu peněženek. Stejné scénáře běží proti in-memory
 * i SQLite repozitáři — v produkci filtruje SQL `LIKE`, in-memory jen v testech.
 */
class WalletSearchSpec {

    private fun exposedRepo(): WalletRepository {
        val dbFile = File.createTempFile("wallet-search", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction { SchemaUtils.create(UsersTable, ReservationsTable, WalletsTable, WalletTransactionsTable) }
        return ExposedWalletRepository()
    }

    private fun bothRepos(block: suspend (WalletService, WalletRepository) -> Unit) = runBlocking {
        listOf(InMemoryWalletRepository(), exposedRepo()).forEach { repo -> block(WalletService(repo), repo) }
    }

    private suspend fun WalletRepository.guest(code: String, email: String) =
        create(NewWallet(code = code, ownerEmail = email, registeredUserId = null))

    @Test
    fun `search matches part of email case-insensitively`() = bothRepos { svc, repo ->
        val jana = repo.guest("SVIT-AAAA-1111", "Jana.Novakova@seznam.cz")
        repo.guest("SVIT-BBBB-2222", "petr@gmail.com")

        val page = svc.getAll(page = 0, pageSize = 20, searchQuery = "novakova@SEZ")
        assertEquals(listOf(jana.id), page.items.map { it.id })
        assertEquals(1L, page.totalCount)
    }

    @Test
    fun `search matches part of wallet code case-insensitively`() = bothRepos { svc, repo ->
        repo.guest("SVIT-AAAA-1111", "jana@seznam.cz")
        val petr = repo.guest("SVIT-BBBB-2222", "petr@gmail.com")

        val page = svc.getAll(page = 0, pageSize = 20, searchQuery = "bbbb-22")
        assertEquals(listOf(petr.id), page.items.map { it.id })
        assertEquals(1L, page.totalCount)
    }

    @Test
    fun `blank search is no filter`() = bothRepos { svc, repo ->
        repo.guest("SVIT-AAAA-1111", "jana@seznam.cz")
        repo.guest("SVIT-BBBB-2222", "petr@gmail.com")

        assertEquals(2L, svc.getAll(page = 0, pageSize = 20, searchQuery = "   ").totalCount)
        assertEquals(2L, svc.getAll(page = 0, pageSize = 20, searchQuery = null).totalCount)
    }

    @Test
    fun `search combines with hideEmpty and count matches items`() = bothRepos { svc, repo ->
        repo.guest("SVIT-AAAA-1111", "jana@seznam.cz")
        val full = repo.guest("SVIT-BBBB-2222", "jana.druha@seznam.cz")
        repo.guest("SVIT-CCCC-3333", "petr@gmail.com")
        svc.credit(full.id, 100.0, WalletTransactionReason.ADMIN_CREDIT)

        // Počet musí sedět s filtrem, jinak stránkování ukazuje prázdné stránky.
        val page = svc.getAll(page = 0, pageSize = 20, hideEmpty = true, searchQuery = "jana")
        assertEquals(listOf(full.id), page.items.map { it.id })
        assertEquals(1L, page.totalCount)
    }
}
