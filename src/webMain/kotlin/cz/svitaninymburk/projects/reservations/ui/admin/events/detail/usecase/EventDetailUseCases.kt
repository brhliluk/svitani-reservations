package cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.event.AddSeriesLessonRequest
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.UpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitUseCase
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/** Vytvoří update request z lekce s obrácenou hodnotou isDropIn (ostatní pole beze změny). */
fun toggleDropInRequest(lesson: EventInstance): UpdateEventInstanceRequest =
    UpdateEventInstanceRequest(
        title = lesson.title,
        description = lesson.description,
        startDateTime = lesson.startDateTime,
        endDateTime = lesson.endDateTime,
        price = lesson.price,
        capacity = lesson.capacity,
        allowedPaymentTypes = lesson.allowedPaymentTypes,
        customFields = lesson.customFields,
        isDropIn = !lesson.isDropIn,
    )

// --- UseCase třídy (tenké, vrací Either) ---

class EventDetailQueries(private val admin: AdminServiceInterface) {
    suspend fun detail(id: Uuid, isSeries: Boolean) = admin.getEventDetail(id, isSeries)
}

/** Kolik záznamů historie se načte najednou. */
const val AUDIT_PAGE_SIZE = 25

class EventAuditLogQueries(private val admin: AdminServiceInterface) {
    suspend fun page(id: Uuid, isSeries: Boolean, page: Int, category: AuditCategory?) =
        admin.getEventAuditLog(id, isSeries, page, AUDIT_PAGE_SIZE, category)
}

class EventLifecycleUseCase(private val admin: AdminServiceInterface) {
    suspend fun delete(id: Uuid, isSeries: Boolean, refund: Boolean) =
        if (isSeries) admin.deleteEventSeries(id, refund) else admin.deleteEventInstance(id, refund)

    suspend fun cancel(id: Uuid, isSeries: Boolean, refund: Boolean) =
        if (isSeries) admin.cancelEventSeries(id, refund) else admin.cancelEventInstance(id, refund)
}

class SeriesLessonsUseCase(private val admin: AdminServiceInterface) {
    suspend fun instances(seriesId: Uuid) = admin.getSeriesInstances(seriesId, page = 0, pageSize = 200)

    suspend fun add(seriesId: Uuid, start: LocalDateTime, end: LocalDateTime, dropIn: Boolean) =
        admin.addSeriesLesson(
            AddSeriesLessonRequest(seriesId = seriesId, startDateTime = start, endDateTime = end, isDropIn = dropIn)
        )

    suspend fun cancel(lessonId: Uuid) = admin.cancelSeriesLesson(lessonId)

    suspend fun toggleDropIn(lesson: EventInstance) =
        admin.updateEventInstance(lesson.id, toggleDropInRequest(lesson))
}

class AdminReservationUseCase(
    private val admin: AdminServiceInterface,
    private val reservation: ReservationServiceInterface,
    private val event: EventServiceInterface,
) {
    private val submitter = ReservationSubmitUseCase(reservation)

    suspend fun target(id: Uuid, isSeries: Boolean): Either<EventError, ReservationTarget> =
        if (isSeries) event.getSeriesDetail(id).map { ReservationTarget.Series(it.series) }
        else event.getInstance(id).map { ReservationTarget.Instance(it) }

    /** Admin zakládá rezervaci za někoho jiného, proto `userId = null`. */
    suspend fun submit(target: ReservationTarget, form: ReservationFormData, acknowledgedDuplicate: Boolean = false) =
        submitter.submit(target, form, userId = null, acknowledgedDuplicate = acknowledgedDuplicate)

    suspend fun confirmPayment(reservationId: Uuid) = admin.markReservationAsPaid(reservationId)

    suspend fun cancel(reservationId: Uuid) = reservation.cancelReservation(reservationId)
}
