package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import dev.kilua.rpc.annotations.RpcService

@RpcService
interface EmailService {
    suspend fun sendReservationConfirmation(
        toEmail: String,
        reservation: Reservation,
        bankAccount: String,
        qrCodeImage: ByteArray, // QR kód jako pole bytů (PNG)
    ): Either<EmailError.SendReservationConfirmation, Unit>

    suspend fun sendCancellationNotice(toEmail: String, reservationId: String): Either<EmailError.SendCancellation, Unit>

    suspend fun sendPaymentReceivedConfirmation(reservation: Reservation): Either<EmailError.SendReservationConfirmation, Unit>
    suspend fun sendPaymentNotPaidInFull(reservation: Reservation, paymentInfo: BankTransaction, bankAccount: String, qrCodeImage: String): Either<EmailError.SendReservationConfirmation, Unit>
}