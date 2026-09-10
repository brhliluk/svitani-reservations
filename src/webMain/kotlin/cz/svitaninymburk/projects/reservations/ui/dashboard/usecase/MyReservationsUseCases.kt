package cz.svitaninymburk.projects.reservations.ui.dashboard.usecase

import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/**
 * Jak se rezervace platí, jak to má karta napsat. `null` = nepsat nic:
 * u rezervace zdarma by "Převodem" vedle "Zdarma" jen protiřečilo samo sobě.
 *
 * Není to totéž jako `reservationPaymentMethod` v admin přehledu — ten rozlišuje
 * i platbu z peněženky, protože jeho DTO nese `walletDeductedAmount`.
 * [MyReservationListItem] ho nemá, takže zdejší karta o peněžence nic neví.
 */
enum class MyReservationPaymentMethod { CASH, TRANSFER }

fun myReservationPaymentMethod(item: MyReservationListItem): MyReservationPaymentMethod? = when {
    item.isFree -> null
    item.paymentType == PaymentInfo.Type.ON_SITE -> MyReservationPaymentMethod.CASH
    else -> MyReservationPaymentMethod.TRANSFER
}

/**
 * Kurz se otevírá klikem na název, jednorázová akce klikem kamkoli do hlavičky —
 * u kurzu je pod hlavičkou rozbalovací seznam lekcí a klikací celá plocha by
 * navigovala i při snaze rozkliknout termín.
 */
fun cardOpensOnTitleOnly(item: MyReservationListItem): Boolean = item.isSeries

// --- UseCase třídy (tenké, vrací Either) ---

class MyReservationsQueries(private val authenticated: AuthenticatedReservationServiceInterface) {
    suspend fun reservations(userId: Uuid) = authenticated.getReservations(userId)
}

/**
 * Termíny kurzu chodí přes guest-callable `getSeriesLessons` — pro majitele vrací
 * totéž a stejnou metodu volá i hostovský detail rezervace.
 */
class SeriesLessonsQueries(private val reservations: ReservationServiceInterface) {
    suspend fun lessons(reservationId: Uuid) = reservations.getSeriesLessons(reservationId)
}
