package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.svitaninymburk.projects.reservations.audit.AuditEvent
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.repository.audit.AuditRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.util.auditSubjectFor
import cz.svitaninymburk.projects.reservations.util.withAuditSubject
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.reflect.jvm.jvmName
import kotlin.uuid.Uuid

/**
 * Pošle znovu mail, který je v historii vedený pod daným záznamem.
 *
 * Existuje kvůli tomu, že odeslání mailu se nedá vzít zpět ani zopakovat samo:
 * když SMTP zprávu odmítne i po opakování (viz `SEND_RETRY_DELAYS` v [GmailEmailService]),
 * zůstane po ní jen řádek FAILURE v historii a zákazník nemá platební údaje.
 *
 * Kopie odeslané zprávy se neuchovává, takže se mail skládá znovu z **aktuálního**
 * stavu rezervace — QR kód tedy nese dnešní nedoplatek a iCal dnešní termín. Proto
 * to umí jen ty typy, které z rezervace poskládat jdou; seznam drží
 * `RESENDABLE_EMAIL_TYPES` u [AuditEvent].
 *
 * Posílá se na dnešní kontaktní e-mail rezervace, ne na adresu z historického
 * záznamu: když admin mezitím opravil překlep v adrese, má mail dojít na opravenou.
 *
 * Nový pokus se do historie zapíše sám — [emailService] je obalený
 * [AuditingEmailService] a [withAuditSubject] mu dodá vazbu na akci.
 */
class EmailResendService(
    private val auditRepository: AuditRepository,
    private val reservationRepository: ReservationRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val emailService: EmailService,
    private val qrCodeService: QrCodeGeneratorService,
    private val appBaseUrl: String,
) {

    private val logger = KtorSimpleLogger(this::class.jvmName)

    suspend fun resend(auditEventId: Uuid): Either<AdminError.ResendEmail, String> = either {
        val entry = ensureNotNull(auditRepository.findById(auditEventId)) {
            AdminError.ResendEmail.AuditEventNotFound
        }
        ensure(entry.isResendable) { AdminError.ResendEmail.NotResendable }

        val reservationId = ensureNotNull(entry.reservationId) { AdminError.ResendEmail.NotResendable }
        val reservation = ensureNotNull(reservationRepository.findById(reservationId)) {
            AdminError.ResendEmail.ReservationNotFound
        }
        val target = ensureNotNull(targetFor(reservation)) { AdminError.ResendEmail.TargetNotFound }

        logger.info(
            "Resending ${entry.type} for reservation ${reservation.id} to ${reservation.contactEmail} " +
                "(audit entry $auditEventId, original outcome ${entry.outcome})"
        )

        val result = withAuditSubject(auditSubjectFor(target, reservation.id)) {
            send(entry.type, reservation, target)
        }
        ensureNotNull(result) { AdminError.ResendEmail.NotResendable }
            .mapLeft { AdminError.ResendEmail.SendFailed(it.localizedMessage) }
            .bind()

        reservation.contactEmail
    }

    /**
     * `null` pro typ, který poskládat neumíme. Pojistka proti tomu, aby rozšíření
     * `RESENDABLE_EMAIL_TYPES` beze změny tady tiše poslalo něco jiného, než na co
     * admin klikl.
     */
    private suspend fun send(
        type: AuditEventType,
        reservation: Reservation,
        target: ReservationTarget,
    ): Either<EmailError, Unit>? = when (type) {
        AuditEventType.EMAIL_RESERVATION_CONFIRMATION -> emailService.sendReservationConfirmation(
            toEmail = reservation.contactEmail,
            reservation = reservation,
            target = target,
            bankAccount = qrCodeService.accountNumber,
            qrCodeImage = qrCodeFor(reservation),
            icalBytes = icalFor(target, reservation),
        )

        AuditEventType.EMAIL_WAITLIST_PROMOTION -> emailService.sendWaitlistPromotion(
            toEmail = reservation.contactEmail,
            reservation = reservation,
            target = target,
            bankAccount = qrCodeService.accountNumber,
            qrCodeImage = qrCodeFor(reservation),
            icalBytes = icalFor(target, reservation),
        )

        AuditEventType.EMAIL_WAITLIST_CONFIRMATION -> emailService.sendWaitlistConfirmation(
            toEmail = reservation.contactEmail,
            eventTitle = target.title,
            contactName = reservation.contactName,
            reservationId = reservation.id,
            locale = reservation.locale,
        )

        AuditEventType.EMAIL_PAYMENT_RECEIVED -> emailService.sendPaymentReceivedConfirmation(reservation)

        AuditEventType.EMAIL_CANCELLATION_NOTICE -> emailService.sendCancellationNotice(
            toEmail = reservation.contactEmail,
            eventTitle = target.title,
            reservationId = reservation.id,
            locale = reservation.locale,
        )

        else -> null
    }

    private suspend fun targetFor(reservation: Reservation): ReservationTarget? =
        when (val reference = reservation.reference) {
            is Reference.Instance -> eventInstanceRepository.get(reference.id)?.let { ReservationTarget.Instance(it) }
            is Reference.Series -> eventSeriesRepository.get(reference.id)?.let { series ->
                ReservationTarget.Series(
                    series.copy(lessonCount = eventInstanceRepository.countActiveBySeries(series.id).toInt())
                )
            }
        }

    /** QR kód dává smysl jen u převodem placené rezervace — stejně jako při prvním odeslání. */
    private fun qrCodeFor(reservation: Reservation): ByteArray? =
        if (reservation.paymentType == PaymentInfo.Type.BANK_TRANSFER) qrCodeService.generateQrPng(reservation) else null

    private fun icalFor(target: ReservationTarget, reservation: Reservation): ByteArray = when (target) {
        is ReservationTarget.Instance -> ICalGenerator.forInstance(target.event, reservation.id, appBaseUrl)
        is ReservationTarget.Series -> ICalGenerator.forSeries(target.series, reservation.id, appBaseUrl)
    }.toByteArray(Charsets.UTF_8)
}
