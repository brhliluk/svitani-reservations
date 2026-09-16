package cz.svitaninymburk.projects.reservations.reservation

import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid


/** Nulová (nebo záporná) cena znamená, že se nic neplatí. Jediné místo, kde ta hranice žije. */
fun isFreePrice(totalPrice: Double): Boolean = totalPrice <= 0.0

@Serializable
data class Reservation(
    val id: Uuid,
    val reference: Reference,
    val registeredUserId: Uuid? = null,

    val contactName: String,
    val contactEmail: String,
    val contactPhone: String? = null,

    val seatCount: Int = 1,
    val totalPrice: Double,
    val paidAmount: Double = 0.0,

    val status: Status,
    val createdAt: Instant,

    val customValues: Map<String, CustomFieldValue>,

    val paymentType: PaymentInfo.Type,
    val variableSymbol: String? = null, // VS pro párování platby
    val paymentPairingToken: String? = null, // Interní ID pro bankovní API
    val locale: String = "cs",
    val walletId: Uuid? = null,
    val walletDeductedAmount: Double = 0.0,
) {
    val unpaidAmount: Double get() = totalPrice - paidAmount

    /**
     * Rezervace, za kterou se nic neplatí. Server podle toho rezervaci zakládá
     * rovnou potvrzenou a UI podle toho skrývá platební instrukce (i u
     * historických rezervací, které v DB ještě zůstaly ve stavu PENDING_PAYMENT).
     */
    val isFree: Boolean get() = isFreePrice(totalPrice)

    @Serializable
    enum class Status {
        PENDING_PAYMENT,
        CONFIRMED,
        CANCELLED,
        REJECTED,
        WAITLISTED,
    }
}

@Serializable
sealed interface Reference {
    val id: Uuid
    @Serializable @SerialName("instance")
    data class Instance(override val id: Uuid): Reference
    @Serializable @SerialName("series")
    data class Series(override val id: Uuid): Reference
}

@Serializable
sealed interface ReservationTarget {
    val id: Uuid
    val title: String
    val price: Double
    val allowedPaymentTypes: List<PaymentInfo.Type>
    val maxCapacity: Int
    /** Když je false, rezervační formulář pole s počtem míst skryje a rezervuje se vždy 1 místo. */
    val allowMultipleSeats: Boolean
    val customFields: List<CustomFieldDefinition>
    val startDateTime: LocalDateTime
    val endDateTime: LocalDateTime

    @Serializable
    @SerialName("instance")
    data class Instance(val event: EventInstance) : ReservationTarget {
        override val id = event.id
        override val title = event.title
        override val price = event.price
        override val allowedPaymentTypes = event.allowedPaymentTypes
        override val maxCapacity = event.capacity - event.occupiedSpots
        override val allowMultipleSeats = event.allowMultipleSeats
        override val startDateTime = event.startDateTime
        override val endDateTime = event.endDateTime
        override val customFields = event.customFields
    }

    @Serializable
    @SerialName("series")
    data class Series(val series: EventSeries) : ReservationTarget {
        override val id = series.id
        override val title = series.title
        override val price = series.price
        override val allowedPaymentTypes = series.allowedPaymentTypes
        override val maxCapacity = series.capacity - series.occupiedSpots
        override val allowMultipleSeats = series.allowMultipleSeats
        override val startDateTime = LocalDateTime(date = series.startDate, time = LocalTime(0,0))
        override val endDateTime = LocalDateTime(date = series.endDate, time = LocalTime(23,0))
        override val customFields = series.customFields
    }
}

interface ReservationRequestData {
    val seatCount: Int
    val contactName: String
    val contactEmail: String
    val contactPhone: String
    val paymentType: PaymentInfo.Type
    val customValues: Map<String, CustomFieldValue>
    val locale: String
}

@Serializable
data class CreateInstanceReservationRequest(
    val eventInstanceId: Uuid,
    override val seatCount: Int = 1,
    override val contactName: String,
    override val contactEmail: String,
    override val contactPhone: String,
    override val paymentType: PaymentInfo.Type,
    override val customValues: Map<String, CustomFieldValue>,
    override val locale: String = "cs",
    val walletCode: String? = null,
    /**
     * Uživatel viděl varování, že na tuhle akci už rezervaci má, a chce pokračovat.
     * Default `false` znamená, že každý nový klient je chráněný, dokud se výslovně neodhlásí.
     */
    val acknowledgedDuplicate: Boolean = false,
) : ReservationRequestData

@Serializable
data class CreateSeriesReservationRequest(
    val eventSeriesId: Uuid,
    override val seatCount: Int = 1,
    override val contactName: String,
    override val contactEmail: String,
    override val contactPhone: String,
    override val paymentType: PaymentInfo.Type,
    override val customValues: Map<String, CustomFieldValue>,
    override val locale: String = "cs",
    val walletCode: String? = null,
    /**
     * Uživatel viděl varování, že na tuhle akci už rezervaci má, a chce pokračovat.
     * Default `false` znamená, že každý nový klient je chráněný, dokud se výslovně neodhlásí.
     */
    val acknowledgedDuplicate: Boolean = false,
) : ReservationRequestData

@Serializable
data class ReservationDetail(
    val reservation: Reservation,
    val target: ReservationTarget?,
    val accountNumber: String,
    val waitlistPosition: Int? = null,
    /** Uzávěrka pro storno s nárokem na kredit, spočítaná serverem. */
    val cancellationDeadline: Instant? = null,
    /**
     * Přihlášený uživatel si tuhle rezervaci bez účtu může připsat k účtu. Počítá to
     * server — zná volajícího z JWT a jen on smí rozhodnout, čí e-mail se s čím shoduje.
     * Klient tím jen řídí, jestli tlačítko ukázat; samotné přivlastnění si podmínky ověří znovu.
     */
    val claimable: Boolean = false,
)

@Serializable
data class MyReservationListItem(
    val id: Uuid,
    val eventTitle: String,
    val startDateTime: LocalDateTime,
    val seatCount: Int,
    val totalPrice: Double,
    val status: Reservation.Status,
    val paymentType: PaymentInfo.Type,
    val variableSymbol: String?,
    val isSeries: Boolean,
) {
    val isFree: Boolean get() = isFreePrice(totalPrice)
}

@Serializable
data class SeriesLessonItem(
    val instanceId: Uuid,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val isCancelled: Boolean,
    val isOptedOut: Boolean,
    val isLateCancellation: Boolean,
    /**
     * Absolutní začátek lekce a uzávěrka pro včasnou omluvenku, obojí spočítané
     * serverem v provozní zóně. Prohlížeč nemá databázi časových pásem, takže by
     * si je sám spočítal v zóně návštěvníka a sliboval jiný termín, než jaký
     * backend uplatní.
     */
    val startsAt: Instant? = null,
    val optOutDeadline: Instant? = null,
)

/**
 * Termíny kurzu tak, jak je potřebuje odhlašovací UI. Neveze celou rezervaci —
 * chodí i na rezervace bez účtu, které chrání jen znalost UUID, takže se
 * kontaktní údaje ven netahají.
 */
@Serializable
data class SeriesLessonsView(
    val lessons: List<SeriesLessonItem>,
    /** Kolik je na rezervaci zaplaceno — pro náhled kreditu v dialogu. */
    val paidAmount: Double,
    /** Kolik už bylo za včasné omluvenky vráceno — kredit se stropuje zaplacenou částkou. */
    val alreadyRefunded: Double,
    /** Kredit za jednu včas odhlášenou lekci a jedno místo; null = kurz kredit nevrací. */
    val lessonRefundAmount: Double? = null,
    /** Počet míst rezervace — omluvenka uvolní všechna, takže se kredit násobí. */
    val seatCount: Int = 1,
    /** Rezervace bez účtu — UI musí nabídnout pole na kód peněženky. */
    val isAnonymousReservation: Boolean = false,
)

@Serializable
data class CancellationResult(
    val walletCode: String? = null,
    val walletCreditAmount: Double? = null,
)