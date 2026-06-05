package cz.svitaninymburk.projects.reservations.error


sealed interface PaymentPairingError : AppError {
    sealed interface CheckAndPairPayments : PaymentPairingError

    data class Upstream(val exception: Exception, val message: String) : CheckAndPairPayments
    data class Failed(val message: String) : CheckAndPairPayments
}

val PaymentPairingError.localizedMessage: String get() = when (this) {
    is PaymentPairingError.Failed -> message
    is PaymentPairingError.Upstream -> exception.stackTraceToString()
}