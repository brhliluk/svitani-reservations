package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import dev.kilua.rpc.annotations.RpcService
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

@RpcService
interface EmailService {
    suspend fun sendReservationConfirmation(
        toEmail: String,
        reservation: Reservation,
        target: ReservationTarget,
        bankAccount: String,
        qrCodeImage: ByteArray?,
        icalBytes: ByteArray,
    ): Either<EmailError.SendReservationConfirmation, Unit>

    suspend fun sendCancellationNotice(toEmail: String, eventTitle: String, reservationId: Uuid, locale: String): Either<EmailError.SendCancellation, Unit>

    suspend fun sendPaymentReceivedConfirmation(reservation: Reservation): Either<EmailError.SendPaymentConfirmation, Unit>
    suspend fun sendPaymentNotPaidInFull(reservation: Reservation, paymentInfo: BankTransaction, bankAccount: String, qrCodeImage: String): Either<EmailError.SendPaymentNotPaidInFull, Unit>

    suspend fun sendPasswordResetEmail(toEmail: String, resetToken: String) : Either<EmailError.SendPasswordReset, Unit>

    suspend fun sendLessonRescheduledNotification(
        toEmail: String,
        contactName: String,
        seriesTitle: String,
        oldDateTime: LocalDateTime,
        newDateTime: LocalDateTime,
    ): Either<EmailError.SendLessonRescheduled, Unit>

    suspend fun sendLessonCancelledNotification(
        toEmail: String,
        contactName: String,
        seriesTitle: String,
        lessonDateTime: LocalDateTime,
    ): Either<EmailError.SendLessonCancelled, Unit>

    suspend fun sendLessonOptOutNotice(
        toEmail: String,
        eventTitle: String,
        lessonDate: LocalDate,
        isLateCancellation: Boolean,
        locale: String,
    ): Either<EmailError.SendCancellation, Unit>

}

interface LectorEmailService {
    suspend fun sendLectorReservationNotification(
        lectorEmail: String,
        contactName: String,
        contactEmail: String,
        contactPhone: String?,
        seatCount: Int,
        eventTitle: String,
        occupiedSpots: Int,
        capacity: Int,
        locale: String,
    ): Either<EmailError.SendLectorReservation, Unit>

    suspend fun sendLectorCancellationNotification(
        lectorEmail: String,
        contactName: String,
        eventTitle: String,
        seatCount: Int,
        occupiedSpots: Int,
        capacity: Int,
        locale: String,
    ): Either<EmailError.SendLectorCancellation, Unit>

    suspend fun sendLectorLessonOptOutNotification(
        lectorEmail: String,
        contactName: String,
        eventTitle: String,
        lessonDate: LocalDate,
        isLateCancellation: Boolean,
        locale: String,
    ): Either<EmailError.SendLectorCancellation, Unit>
}