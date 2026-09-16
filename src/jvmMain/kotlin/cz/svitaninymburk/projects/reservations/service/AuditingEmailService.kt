package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.audit.AuditOutcome
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.LectorTarget
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Zaznamená každý pokus o odeslání mailu — odeslaný i neodeslaný.
 *
 * Je to dekorátor, ne úprava call-sitů: `GmailEmailService` se v DI obalí tímhle
 * a všech ~25 volání se tím pokryje naráz, včetně těch, která dnes výsledek
 * ignorují úplně (`Auth.kt:164`, `Payment.kt:102`, hromadná storna v `Admin.kt`,
 * `WalletReset.kt:47`, `RefundService.kt:72`).
 *
 * Ke které akci mail patří, se bere z `AuditSubject`, který kolem operace nastaví
 * doménová služba — viz `util/AuditContext.kt`. Maily bez vazby na akci
 * (reset hesla, upozornění na vypršení kreditu) se zapíšou bez subjektu.
 */
/**
 * SMTP chyby bývají přes 300 znaků, z nichž nese informaci první věta —
 * zbytek je odkaz do nápovědy a identifikátor spojení. V tabulce historie
 * by dlouhý text přebil všechny ostatní sloupce.
 */
internal fun shortenSmtpError(message: String, limit: Int = 240): String {
    val oneLine = message.replace(Regex("\\s+"), " ").trim()
    return if (oneLine.length <= limit) oneLine else oneLine.take(limit).trimEnd() + "…"
}

class AuditingEmailService(
    private val delegate: EmailService,
    private val lectorDelegate: LectorEmailService,
    private val walletDelegate: WalletEmailService,
    private val audit: AuditService,
) : EmailService, LectorEmailService, WalletEmailService {

    private suspend fun <L : EmailError, R> audited(
        type: AuditEventType,
        recipient: String,
        subjectLabel: String,
        walletCode: String? = null,
        block: suspend () -> Either<L, R>,
    ): Either<L, R> {
        val result = block()
        audit.record(
            type = type,
            subjectLabel = subjectLabel,
            recipient = recipient,
            walletCode = walletCode,
            outcome = if (result.isRight()) AuditOutcome.SUCCESS else AuditOutcome.FAILURE,
            detail = result.leftOrNull()?.localizedMessage?.let(::shortenSmtpError),
        )
        return result
    }

    // --- EmailService -------------------------------------------------------

    override suspend fun sendReservationConfirmation(
        toEmail: String,
        reservation: Reservation,
        target: ReservationTarget,
        bankAccount: String,
        qrCodeImage: ByteArray?,
        icalBytes: ByteArray,
    ) = audited(AuditEventType.EMAIL_RESERVATION_CONFIRMATION, toEmail, target.title) {
        delegate.sendReservationConfirmation(toEmail, reservation, target, bankAccount, qrCodeImage, icalBytes)
    }

    override suspend fun sendCancellationNotice(toEmail: String, eventTitle: String, reservationId: Uuid, locale: String) =
        audited(AuditEventType.EMAIL_CANCELLATION_NOTICE, toEmail, eventTitle) {
            delegate.sendCancellationNotice(toEmail, eventTitle, reservationId, locale)
        }

    override suspend fun sendPaymentReceivedConfirmation(reservation: Reservation) =
        audited(AuditEventType.EMAIL_PAYMENT_RECEIVED, reservation.contactEmail, reservation.contactName) {
            delegate.sendPaymentReceivedConfirmation(reservation)
        }

    override suspend fun sendPaymentNotPaidInFull(
        reservation: Reservation,
        paymentInfo: BankTransaction,
        bankAccount: String,
        qrCodeImage: ByteArray,
    ) = audited(AuditEventType.EMAIL_PAYMENT_NOT_PAID_IN_FULL, reservation.contactEmail, reservation.contactName) {
        delegate.sendPaymentNotPaidInFull(reservation, paymentInfo, bankAccount, qrCodeImage)
    }

    override suspend fun sendPasswordResetEmail(toEmail: String, resetToken: String) =
        audited(AuditEventType.EMAIL_PASSWORD_RESET, toEmail, toEmail) {
            delegate.sendPasswordResetEmail(toEmail, resetToken)
        }

    override suspend fun sendReservationClaimEmail(
        toEmail: String,
        reservations: List<MyReservationListItem>,
        claimToken: String,
        locale: String,
    ) = audited(AuditEventType.EMAIL_RESERVATION_CLAIM, toEmail, toEmail) {
        delegate.sendReservationClaimEmail(toEmail, reservations, claimToken, locale)
    }

    override suspend fun sendLessonRescheduledNotification(
        toEmail: String,
        contactName: String,
        seriesTitle: String,
        oldDateTime: LocalDateTime,
        newDateTime: LocalDateTime,
        locale: String,
    ) = audited(AuditEventType.EMAIL_LESSON_RESCHEDULED, toEmail, contactName) {
        delegate.sendLessonRescheduledNotification(toEmail, contactName, seriesTitle, oldDateTime, newDateTime, locale)
    }

    override suspend fun sendLessonCancelledNotification(
        toEmail: String,
        contactName: String,
        seriesTitle: String,
        lessonDateTime: LocalDateTime,
        locale: String,
    ) = audited(AuditEventType.EMAIL_LESSON_CANCELLED, toEmail, contactName) {
        delegate.sendLessonCancelledNotification(toEmail, contactName, seriesTitle, lessonDateTime, locale)
    }

    override suspend fun sendLessonOptOutNotice(
        toEmail: String,
        eventTitle: String,
        lessonDate: LocalDate,
        isLateCancellation: Boolean,
        locale: String,
    ) = audited(AuditEventType.EMAIL_LESSON_OPT_OUT, toEmail, eventTitle) {
        delegate.sendLessonOptOutNotice(toEmail, eventTitle, lessonDate, isLateCancellation, locale)
    }

    override suspend fun sendWaitlistConfirmation(
        toEmail: String,
        eventTitle: String,
        contactName: String,
        reservationId: Uuid,
        locale: String,
    ) = audited(AuditEventType.EMAIL_WAITLIST_CONFIRMATION, toEmail, contactName) {
        delegate.sendWaitlistConfirmation(toEmail, eventTitle, contactName, reservationId, locale)
    }

    override suspend fun sendWaitlistPromotion(
        toEmail: String,
        reservation: Reservation,
        target: ReservationTarget,
        bankAccount: String,
        qrCodeImage: ByteArray?,
        icalBytes: ByteArray,
    ) = audited(AuditEventType.EMAIL_WAITLIST_PROMOTION, toEmail, reservation.contactName) {
        delegate.sendWaitlistPromotion(toEmail, reservation, target, bankAccount, qrCodeImage, icalBytes)
    }

    // --- LectorEmailService -------------------------------------------------

    override suspend fun sendLectorReservationNotification(
        lectorEmail: String,
        contactName: String,
        contactEmail: String,
        contactPhone: String?,
        seatCount: Int,
        eventTitle: String,
        target: LectorTarget,
        occupiedSpots: Int,
        capacity: Int,
        locale: String,
    ) = audited(AuditEventType.EMAIL_LECTOR_RESERVATION, lectorEmail, contactName) {
        lectorDelegate.sendLectorReservationNotification(
            lectorEmail, contactName, contactEmail, contactPhone, seatCount, eventTitle, target, occupiedSpots, capacity, locale,
        )
    }

    override suspend fun sendLectorCancellationNotification(
        lectorEmail: String,
        contactName: String,
        eventTitle: String,
        target: LectorTarget,
        seatCount: Int,
        occupiedSpots: Int,
        capacity: Int,
        locale: String,
    ) = audited(AuditEventType.EMAIL_LECTOR_CANCELLATION, lectorEmail, contactName) {
        lectorDelegate.sendLectorCancellationNotification(
            lectorEmail, contactName, eventTitle, target, seatCount, occupiedSpots, capacity, locale,
        )
    }

    override suspend fun sendLectorLessonOptOutNotification(
        lectorEmail: String,
        contactName: String,
        eventTitle: String,
        lessonDate: LocalDate,
        isLateCancellation: Boolean,
        locale: String,
    ) = audited(AuditEventType.EMAIL_LECTOR_LESSON_OPT_OUT, lectorEmail, contactName) {
        lectorDelegate.sendLectorLessonOptOutNotification(lectorEmail, contactName, eventTitle, lessonDate, isLateCancellation, locale)
    }

    // --- WalletEmailService -------------------------------------------------

    override suspend fun sendWalletCredited(
        toEmail: String,
        walletCode: String,
        creditedAmount: Double,
        newBalance: Double,
        resetMonth: Int,
        resetDay: Int,
        locale: String,
    ) = audited(AuditEventType.EMAIL_WALLET_CREDITED, toEmail, walletCode, walletCode = walletCode) {
        walletDelegate.sendWalletCredited(toEmail, walletCode, creditedAmount, newBalance, resetMonth, resetDay, locale)
    }

    override suspend fun sendWalletApplied(
        toEmail: String,
        walletCode: String,
        deductedAmount: Double,
        remainingBalance: Double,
        locale: String,
    ) = audited(AuditEventType.EMAIL_WALLET_APPLIED, toEmail, walletCode, walletCode = walletCode) {
        walletDelegate.sendWalletApplied(toEmail, walletCode, deductedAmount, remainingBalance, locale)
    }

    override suspend fun sendWalletResetWarning(
        toEmail: String,
        walletCode: String,
        currentBalance: Double,
        resetMonth: Int,
        resetDay: Int,
        locale: String,
    ) = audited(AuditEventType.EMAIL_WALLET_RESET_WARNING, toEmail, walletCode, walletCode = walletCode) {
        walletDelegate.sendWalletResetWarning(toEmail, walletCode, currentBalance, resetMonth, resetDay, locale)
    }
}
