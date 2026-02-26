package cz.svitaninymburk.projects.reservations.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("admin") sealed interface AdminError : AppError {
    @Serializable @SerialName("get") sealed interface GetSummary : AppError

    @Serializable data class FailedToGetSummary(val id: String) : GetSummary
}