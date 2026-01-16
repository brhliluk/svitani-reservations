package cz.svitaninymburk.projects.reservations.error


sealed interface EmailError : AppError {
    sealed interface SendReservationConfirmation: EmailError
    sealed interface SendCancellation: EmailError
    sealed interface SendPaymentConfirmation : EmailError
    sealed interface SendPaymentNotPaidInFull : EmailError

    data class SendReservationConfirmationFailed(val message: String) : SendReservationConfirmation
    data class SendCancellationFailed(val message: String) : SendCancellation
    data class SendPaymentConfirmationFailed(val message: String) : SendPaymentConfirmation
    data class SendPaymentNotPaidInFullFailed(val message: String) : SendPaymentNotPaidInFull
}

val EmailError.localizedMessage: String get() = when (this) {
    is EmailError.SendReservationConfirmationFailed -> message
    is EmailError.SendCancellationFailed -> message
    is EmailError.SendPaymentConfirmationFailed -> message
    is EmailError.SendPaymentNotPaidInFullFailed -> message
}
