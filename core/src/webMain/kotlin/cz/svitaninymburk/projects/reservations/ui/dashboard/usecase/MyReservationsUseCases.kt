package cz.svitaninymburk.projects.reservations.ui.dashboard.usecase

import cz.svitaninymburk.projects.reservations.i18n.AppStrings
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.ReservationKind
import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.util.humanReadable
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
    item.paymentType == PaymentType.ON_SITE -> MyReservationPaymentMethod.CASH
    else -> MyReservationPaymentMethod.TRANSFER
}

/**
 * Kurz se otevírá klikem na název, jednorázová akce klikem kamkoli do hlavičky —
 * u kurzu je pod hlavičkou rozbalovací seznam lekcí a klikací celá plocha by
 * navigovala i při snaze rozkliknout termín.
 */
fun cardOpensOnTitleOnly(item: MyReservationListItem): Boolean = item.isSeries

/** Kdy rezervace je: u úseku celý interval („út 14. 10. 9:00–11:30“), jinak začátek. */
fun myReservationWhen(item: MyReservationListItem, strings: AppStrings): String {
    val end = item.endDateTime
    return if (item.kind == ReservationKind.SLOT && end != null) {
        strings.slotInterval(item.startDateTime, end)
    } else {
        item.startDateTime.humanReadable
    }
}

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
