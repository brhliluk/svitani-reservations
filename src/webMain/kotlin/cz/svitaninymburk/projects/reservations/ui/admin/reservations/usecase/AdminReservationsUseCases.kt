package cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase

import cz.svitaninymburk.projects.reservations.reservation.isFreePrice
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import kotlin.uuid.Uuid

const val RESERVATIONS_PAGE_SIZE = 20

// --- Pure helpery (testovatelné bez RPC) ---

/**
 * Prázdný nebo mezerový dotaz není hledání — do RPC posíláme `null`, aby se
 * vrátil celý přehled, ne nulový výsledek pro prázdný řetězec.
 */
fun searchQueryOf(input: String): String? = input.takeIf { it.isNotBlank() }

/**
 * Jak byla rezervace zaplacená. Peněženka může platbu pokrýt celou i jen z části
 * a doplatek pak jde buď na místě, nebo převodem — proto to není jeden příznak,
 * ale pět stavů. U akce zdarma není co platit a vrací se `null`.
 */
enum class ReservationPaymentMethod { WALLET, CASH_AND_WALLET, TRANSFER_AND_WALLET, CASH, TRANSFER }

fun reservationPaymentMethod(
    totalPrice: Double,
    walletDeductedAmount: Double,
    isOnSite: Boolean,
): ReservationPaymentMethod? {
    if (isFreePrice(totalPrice)) return null
    val fullyFromWallet = walletDeductedAmount >= totalPrice && totalPrice > 0
    val partlyFromWallet = walletDeductedAmount > 0 && !fullyFromWallet
    return when {
        fullyFromWallet -> ReservationPaymentMethod.WALLET
        partlyFromWallet && isOnSite -> ReservationPaymentMethod.CASH_AND_WALLET
        partlyFromWallet -> ReservationPaymentMethod.TRANSFER_AND_WALLET
        isOnSite -> ReservationPaymentMethod.CASH
        else -> ReservationPaymentMethod.TRANSFER
    }
}

// --- UseCase třídy (tenké, vrací Either) ---

class AdminReservationsQueries(private val admin: AdminServiceInterface) {
    suspend fun reservations(query: String?, page: Int, pageSize: Int, includeCancelled: Boolean) =
        admin.getAllReservations(query, page, pageSize, includeCancelled)
}

class AdminReservationsMutations(
    private val admin: AdminServiceInterface,
    private val reservations: ReservationServiceInterface,
) {
    suspend fun markAsPaid(id: Uuid) = admin.markReservationAsPaid(id)
    suspend fun cancel(id: Uuid) = reservations.cancelReservation(id)
}
