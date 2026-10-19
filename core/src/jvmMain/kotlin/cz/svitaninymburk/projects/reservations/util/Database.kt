package cz.svitaninymburk.projects.reservations.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * Top-level funkce pro asynchronní spouštění databázových dotazů.
 * Nejdřív přepne kontext na IO vlákno (standardní Kotlin způsob)
 * a pak spustí transakci.
 */
suspend fun <T> dbQuery(block: suspend () -> T): T = withContext(Dispatchers.IO) { suspendTransaction { block() } }