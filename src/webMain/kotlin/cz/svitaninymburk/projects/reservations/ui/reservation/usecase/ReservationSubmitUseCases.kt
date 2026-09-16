package cz.svitaninymburk.projects.reservations.ui.reservation.usecase

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/** Čtyři kombinace cíle a pořadníku — každá je jiné RPC volání. */
enum class ReservationSubmitAction { ReserveInstance, ReserveSeries, JoinWaitlistInstance, JoinWaitlistSeries }

fun reservationSubmitAction(target: ReservationTarget, asWaitlist: Boolean): ReservationSubmitAction = when (target) {
    is ReservationTarget.Instance -> if (asWaitlist) {
        ReservationSubmitAction.JoinWaitlistInstance
    } else {
        ReservationSubmitAction.ReserveInstance
    }
    is ReservationTarget.Series -> if (asWaitlist) {
        ReservationSubmitAction.JoinWaitlistSeries
    } else {
        ReservationSubmitAction.ReserveSeries
    }
}

// --- UseCase třídy (tenké, vrací Either) ---

/**
 * Seam mezi modelem a RPC — díky němu jde odesílání rezervace otestovat
 * bez implementace celého [ReservationServiceInterface].
 */
fun interface ReservationSubmitter {
    suspend fun submit(
        target: ReservationTarget,
        formData: ReservationFormData,
        userId: Uuid?,
        acknowledgedDuplicate: Boolean,
    ): Either<ReservationError.CreateReservation, Reservation>
}

/** Jediné místo, kde se z cíle a příznaku pořadníku vybírá konkrétní volání. */
class ReservationSubmitUseCase(private val reservations: ReservationServiceInterface) : ReservationSubmitter {
    override suspend fun submit(
        target: ReservationTarget,
        formData: ReservationFormData,
        userId: Uuid?,
        acknowledgedDuplicate: Boolean,
    ): Either<ReservationError.CreateReservation, Reservation> {
        val instance = { formData.toCreateInstanceReservationRequest(target.id, acknowledgedDuplicate) }
        val series = { formData.toCreateSeriesReservationRequest(target.id, acknowledgedDuplicate) }
        return when (reservationSubmitAction(target, formData.asWaitlist)) {
            ReservationSubmitAction.ReserveInstance -> reservations.reserveInstance(instance(), userId)
            ReservationSubmitAction.ReserveSeries -> reservations.reserveSeries(series(), userId)
            ReservationSubmitAction.JoinWaitlistInstance -> reservations.joinWaitlistInstance(instance(), userId)
            ReservationSubmitAction.JoinWaitlistSeries -> reservations.joinWaitlistSeries(series(), userId)
        }
    }
}
