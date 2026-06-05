package cz.svitaninymburk.projects.reservations.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("attendance") sealed interface AttendanceError : AppError {
    @Serializable @SerialName("get") sealed interface Get : AttendanceError
    @Serializable @SerialName("set") sealed interface Set : AttendanceError

    @Serializable data object EventNotFound : Get
    @Serializable data object ReservationNotFound : Set
    @Serializable data class SystemError(val message: String) : Get, Set
}
