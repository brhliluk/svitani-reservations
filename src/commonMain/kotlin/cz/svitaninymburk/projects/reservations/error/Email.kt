package cz.svitaninymburk.projects.reservations.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable @SerialName("email") sealed interface EmailError : AppError {
    @Serializable @SerialName("reservation_confirmation") sealed interface SendReservationConfirmation: EmailError
    @Serializable @SerialName("cancellation") sealed interface SendCancellation: EmailError
    @Serializable @SerialName("payment_confirmation") sealed interface SendPaymentConfirmation : EmailError
    @Serializable @SerialName("payment_not_paid_in_full") sealed interface SendPaymentNotPaidInFull : EmailError
    @Serializable @SerialName("password_reset") sealed interface SendPasswordReset : EmailError

    @Serializable data class SendReservationConfirmationFailed(val message: String) : SendReservationConfirmation
    @Serializable data class SendCancellationFailed(val message: String) : SendCancellation
    @Serializable data class SendPaymentConfirmationFailed(val message: String) : SendPaymentConfirmation
    @Serializable data class SendPaymentNotPaidInFullFailed(val message: String) : SendPaymentNotPaidInFull
    @Serializable data class SendPasswordResetFailed(val message: String) : SendPasswordReset
}

val EmailError.localizedMessage: String get() = when (this) {
    is EmailError.SendReservationConfirmationFailed -> message
    is EmailError.SendCancellationFailed -> message
    is EmailError.SendPaymentConfirmationFailed -> message
    is EmailError.SendPaymentNotPaidInFullFailed -> message
    is EmailError.SendPasswordResetFailed -> message
}
