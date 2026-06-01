package cz.svitaninymburk.projects.reservations.wallet

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Wallet(
    val id: Uuid,
    val code: String,
    val ownerEmail: String,
    val registeredUserId: Uuid? = null,
    val balance: Double,
    val createdAt: Instant,
)

@Serializable
data class WalletInfo(
    val code: String,
    val balance: Double,
    val emailMatches: Boolean,
    val seasonResetDay: Int,
    val seasonResetMonth: Int,
)

@Serializable
data class WalletsPage(
    val items: List<Wallet>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int,
)
