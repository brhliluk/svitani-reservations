package cz.svitaninymburk.projects.reservations.slot

import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Služba s rezervací libovolného časového úseku (hlídání dětí). Admin nastaví okna
 * ([AvailabilityRule], [AvailabilityException]) a zákazník si v nich zarezervuje úsek
 * zarovnaný na [step] pro N míst (dětí). Volné úseky se neukládají, počítají se
 * za běhu přes [expandAvailability].
 */
@Serializable
data class SlotService(
    val id: Uuid,
    val title: String,
    val description: String,
    /** Krok zarovnání začátku i konce (15, 30 nebo 60 min, viz [SLOT_STEPS]), počítáno od půlnoci. */
    val step: Duration,
    /** Nejkratší úsek; 0 = bez minima (úsek má vždy aspoň jeden krok). */
    val minDuration: Duration,
    /** Nejdelší úsek; null = bez omezení (úsek stejně nepřesáhne okno dne). */
    val maxDuration: Duration?,
    /** Cena za hodinu a jedno místo (dítě). */
    val pricePerHour: Double,
    /** Kolik míst (dětí) může být obsazeno současně. */
    val capacity: Int,
    val maxSeatsPerBooking: Int,
    val allowedPaymentTypes: List<PaymentType> = listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val ownerEmails: List<String> = emptyList(),
    /** Jak dlouho před začátkem úseku se rezervace zavírá; null = až do začátku. */
    val reservationDeadline: Duration? = null,
    /** Jak daleko dopředu jde rezervovat. */
    val bookingHorizon: Duration,
    /** Nepublikovanou službu vidí a rezervuje jen admin. */
    val isPublished: Boolean = false,
)

/** Pravidelné okno: každý [dayOfWeek] od [from] do [to], platné v rozmezí [validFrom]–[validTo] (včetně). */
@Serializable
data class AvailabilityRule(
    val id: Uuid,
    val serviceId: Uuid,
    val dayOfWeek: DayOfWeek,
    val from: LocalTime,
    val to: LocalTime,
    val validFrom: LocalDate,
    /** null = platí bez konce. */
    val validTo: LocalDate? = null,
)

/**
 * Výjimka pro jeden den. Nahrazuje všechna pravidla toho dne: buď je zavřeno ([closed]),
 * nebo platí náhradní okno [from]–[to].
 */
@Serializable
data class AvailabilityException(
    val id: Uuid,
    val serviceId: Uuid,
    val date: LocalDate,
    val closed: Boolean,
    val from: LocalTime? = null,
    val to: LocalTime? = null,
)

/** Jeden krok okna: kolik míst z [capacity] je v něm ještě volných. */
@Serializable
data class SlotSegment(val start: LocalDateTime, val end: LocalDateTime, val free: Int, val capacity: Int)

/**
 * Kroky všech oken jednoho dne seřazené podle času. Mezera mezi koncem jednoho
 * kroku a začátkem dalšího znamená hranici mezi okny.
 */
@Serializable
data class DayAvailability(val date: LocalDate, val segments: List<SlotSegment>)

/** Úsek obsazený aktivní rezervací; vstup pro [expandAvailability]. */
data class BookedInterval(val start: LocalDateTime, val end: LocalDateTime, val seats: Int)
