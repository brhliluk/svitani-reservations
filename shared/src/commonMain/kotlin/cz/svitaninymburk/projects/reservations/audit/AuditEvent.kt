package cz.svitaninymburk.projects.reservations.audit

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Append-only záznam o tom, co se stalo kolem akce, kurzu nebo rezervace.
 *
 * Proti `payment_events` a `wallet_transactions`, které jsou účetní zdroj pravdy,
 * je tohle *čtecí* model pro admina: nemá cizí klíče a popisky si drží jako text,
 * aby zůstal čitelný i po smazání rezervace nebo akce, ke které patřil.
 */
@Serializable
enum class AuditCategory { RESERVATION, EMAIL, PAYMENT }

@Serializable
enum class AuditEventType(val category: AuditCategory) {
    // Rezervace
    RESERVATION_CREATED(AuditCategory.RESERVATION),
    RESERVATION_WAITLIST_JOINED(AuditCategory.RESERVATION),
    RESERVATION_WAITLIST_PROMOTED(AuditCategory.RESERVATION),
    RESERVATION_CANCELLED(AuditCategory.RESERVATION),
    /** Host si rezervaci bez účtu připsal ke svému účtu (shoda kontaktního e-mailu). */
    RESERVATION_CLAIMED(AuditCategory.RESERVATION),
    RESERVATION_LESSON_OPT_OUT(AuditCategory.RESERVATION),
    /** Admin vzal omluvenku zpět a vrátil účastníka do lekce. */
    RESERVATION_LESSON_OPT_OUT_REVOKED(AuditCategory.RESERVATION),
    LESSON_RESCHEDULED(AuditCategory.RESERVATION),
    LESSON_CANCELLED(AuditCategory.RESERVATION),
    EVENT_CANCELLED(AuditCategory.RESERVATION),

    // Platby
    PAYMENT_PAIRED_AUTO(AuditCategory.PAYMENT),
    PAYMENT_PAIRED_MANUAL(AuditCategory.PAYMENT),
    PAYMENT_PARTIAL(AuditCategory.PAYMENT),

    /**
     * Platba dorazila, ale její variabilní symbol nesedí na žádnou čekající rezervaci.
     * Dřív se taková transakce jen zalogovala a zmizela — přitom je to přesně ten
     * případ, který musí někdo ručně dohledat.
     */
    PAYMENT_UNMATCHED(AuditCategory.PAYMENT),
    PAYMENT_REFUNDED(AuditCategory.PAYMENT),

    // Maily zákazníkům
    EMAIL_RESERVATION_CONFIRMATION(AuditCategory.EMAIL),
    EMAIL_CANCELLATION_NOTICE(AuditCategory.EMAIL),
    EMAIL_PAYMENT_RECEIVED(AuditCategory.EMAIL),
    EMAIL_PAYMENT_NOT_PAID_IN_FULL(AuditCategory.EMAIL),
    EMAIL_PASSWORD_RESET(AuditCategory.EMAIL),
    EMAIL_LESSON_RESCHEDULED(AuditCategory.EMAIL),
    EMAIL_LESSON_CANCELLED(AuditCategory.EMAIL),
    EMAIL_LESSON_OPT_OUT(AuditCategory.EMAIL),
    EMAIL_WAITLIST_CONFIRMATION(AuditCategory.EMAIL),
    EMAIL_WAITLIST_PROMOTION(AuditCategory.EMAIL),
    EMAIL_RESERVATION_CLAIM(AuditCategory.EMAIL),

    // Maily lektorům
    EMAIL_LECTOR_RESERVATION(AuditCategory.EMAIL),
    EMAIL_LECTOR_CANCELLATION(AuditCategory.EMAIL),
    EMAIL_LECTOR_LESSON_OPT_OUT(AuditCategory.EMAIL),

    // Maily k peněžence
    EMAIL_WALLET_CREDITED(AuditCategory.EMAIL),
    EMAIL_WALLET_APPLIED(AuditCategory.EMAIL),
    EMAIL_WALLET_RESET_WARNING(AuditCategory.EMAIL),
}

@Serializable
enum class AuditActorType { CUSTOMER, ADMIN, SYSTEM }

@Serializable
enum class AuditOutcome { SUCCESS, FAILURE }

@Serializable
data class AuditEvent(
    val id: Uuid,
    val occurredAt: Instant,
    val type: AuditEventType,
    val seriesId: Uuid? = null,
    val instanceId: Uuid? = null,
    val reservationId: Uuid? = null,
    /** Kód peněženky, ne id — je unikátní, čitelný a přežije i smazání peněženky. */
    val walletCode: String? = null,
    val actorType: AuditActorType,
    val actorLabel: String,
    val subjectLabel: String,
    val outcome: AuditOutcome? = null,
    val recipient: String? = null,
    val amount: Double? = null,
    val detail: String? = null,
) {
    val category: AuditCategory get() = type.category
}
