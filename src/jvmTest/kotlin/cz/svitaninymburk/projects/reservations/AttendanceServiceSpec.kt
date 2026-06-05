package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.repository.attendance.InMemoryAttendanceRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AttendanceService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AttendanceServiceSpec {
    private fun reservation(instanceId: Uuid, name: String, status: Reservation.Status = Reservation.Status.CONFIRMED) =
        Reservation(
            id = Uuid.random(),
            reference = Reference.Instance(instanceId),
            contactName = name,
            contactEmail = "$name@x.cz",
            totalPrice = 0.0,
            status = status,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.entries.first(),
        )

    @Test
    fun listsAttendanceForInstance() = runBlocking {
        val instanceId = Uuid.random()
        val resRepo = InMemoryReservationRepository()
        val r1 = reservation(instanceId, "Alice")
        resRepo.save(r1)
        val attRepo = InMemoryAttendanceRepository()
        val service = AttendanceService(resRepo, attRepo)

        val result = service.getAttendance(instanceId)
        assertTrue(result.isRight())
        val entries = result.getOrNull()!!.entries
        assertEquals(1, entries.size)
        assertEquals(false, entries.first().checkedIn)

        service.setAttendance(r1.id, true)
        assertEquals(true, service.getAttendance(instanceId).getOrNull()!!.entries.first().checkedIn)
    }

    @Test
    fun cancelledReservationsAreExcluded() = runBlocking {
        val instanceId = Uuid.random()
        val resRepo = InMemoryReservationRepository()
        val active = reservation(instanceId, "Alice", Reservation.Status.CONFIRMED)
        val cancelled = reservation(instanceId, "Bob", Reservation.Status.CANCELLED)
        resRepo.save(active)
        resRepo.save(cancelled)
        val service = AttendanceService(resRepo, InMemoryAttendanceRepository())

        val result = service.getAttendance(instanceId).getOrNull()!!
        assertEquals(1, result.entries.size)
        assertEquals("Alice", result.entries.first().contactName)
    }

    @Test
    fun setAttendanceReturnsNotFoundForMissingReservation() = runBlocking {
        val service = AttendanceService(InMemoryReservationRepository(), InMemoryAttendanceRepository())
        val result = service.setAttendance(Uuid.random(), true)
        assertTrue(result.isLeft())
    }
}
