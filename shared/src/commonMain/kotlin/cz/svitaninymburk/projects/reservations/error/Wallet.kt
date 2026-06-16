package cz.svitaninymburk.projects.reservations.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("wallet") sealed interface WalletError : AppError {
    @Serializable @SerialName("resolve_anonymous") sealed interface ResolveAnonymous : WalletError
    @Serializable @SerialName("validate_for_reservation") sealed interface ValidateForReservation : WalletError

    @Serializable data object NotFound : ResolveAnonymous, ValidateForReservation
    @Serializable data object EmailMismatch : ResolveAnonymous
    @Serializable data object Empty : ValidateForReservation
}
