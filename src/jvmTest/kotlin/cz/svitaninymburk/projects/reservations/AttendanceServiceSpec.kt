package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.attendance.InMemoryAttendanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import cz.svitaninymburk.projects.reservations.service.AttendanceService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AttendanceServiceSpec {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val definitionId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")

    private fun service(
        resRepo: InMemoryReservationRepository = InMemoryReservationRepository(),
        attRepo: InMemoryAttendanceRepository = InMemoryAttendanceRepository(),
        instanceRepo: InMemoryEventInstanceRepository = InMemoryEventInstanceRepository(),
        optOutRepo: InMemorySeriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
    ) = AttendanceService(
        reservationRepository = resRepo,
        attendanceRepository = attRepo,
        eventInstanceRepository = instanceRepo,
        seriesLessonOptOutRepository = optOutRepo,
    )

    private fun reservation(
        reference: Reference,
        name: String,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
    ) = Reservation(
        id = Uuid.random(),
        reference = reference,
        contactName = name,
        contactEmail = "$name@x.cz",
        totalPrice = 0.0,
        status = status,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.entries.first(),
    )

    private fun lesson(id: Uuid, series: Uuid? = null) = EventInstance(
        id = id,
        definitionId = definitionId,
        seriesId = series,
        title = "Lekce",
        description = "",
        startDateTime = LocalDateTime(2026, 9, 1, 10, 0),
        endDateTime = LocalDateTime(2026, 9, 1, 11, 0),
        price = 100.0,
        capacity = 10,
    )

    @Test
    fun listsAttendanceForInstance() = runBlocking {
        val instanceId = Uuid.random()
        val resRepo = InMemoryReservationRepository()
        val r1 = reservation(Reference.Instance(instanceId), "Alice")
        resRepo.save(r1)
        val svc = service(resRepo = resRepo)

        val result = svc.getAttendance(instanceId)
        assertTrue(result.isRight())
        val entries = result.getOrNull()!!.entries
        assertEquals(1, entries.size)
        assertEquals(false, entries.first().checkedIn)

        svc.setAttendance(r1.id, instanceId, true)
        assertEquals(true, svc.getAttendance(instanceId).getOrNull()!!.entries.first().checkedIn)
    }

    @Test
    fun cancelledReservationsAreExcluded() = runBlocking {
        val instanceId = Uuid.random()
        val resRepo = InMemoryReservationRepository()
        resRepo.save(reservation(Reference.Instance(instanceId), "Alice", Reservation.Status.CONFIRMED))
        resRepo.save(reservation(Reference.Instance(instanceId), "Bob", Reservation.Status.CANCELLED))

        val result = service(resRepo = resRepo).getAttendance(instanceId).getOrNull()!!
        assertEquals(1, result.entries.size)
        assertEquals("Alice", result.entries.first().contactName)
    }

    @Test
    fun setAttendanceReturnsNotFoundForMissingReservation() = runBlocking {
        val result = service().setAttendance(Uuid.random(), Uuid.random(), true)
        assertTrue(result.isLeft())
    }

    @Test
    fun `lesson attendance includes course enrollees except those who opted out`() = runBlocking {
        val lessonId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")
        val instanceRepo = InMemoryEventInstanceRepository()
        val resRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()
        instanceRepo.create(lesson(lessonId, series = seriesId))

        resRepo.save(reservation(Reference.Instance(lessonId), "Dropin"))
        resRepo.save(reservation(Reference.Series(seriesId), "Kurzista"))
        val optedOutEnrollment = resRepo.save(reservation(Reference.Series(seriesId), "Omluveny"))
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(),
                reservationId = optedOutEnrollment.id,
                instanceId = lessonId,
                optedOutAt = Clock.System.now(),
                isLateCancellation = false,
            )
        )

        val list = service(
            resRepo = resRepo,
            instanceRepo = instanceRepo,
            optOutRepo = optOutRepo,
        ).getAttendance(lessonId).getOrNull()!!

        assertEquals(
            listOf("Dropin", "Kurzista"),
            list.entries.map { it.contactName }.sorted(),
            "omluvený účastník kurzu v prezenčce být nemá",
        )
        assertEquals(
            mapOf("Dropin" to false, "Kurzista" to true),
            list.entries.associate { it.contactName to it.isCourseEnrollee },
        )
    }

    @Test
    fun `checking in a course enrollee applies to one lesson only`() = runBlocking {
        val lessonA = Uuid.parse("00000000-0000-0000-0000-0000000000c1")
        val lessonB = Uuid.parse("00000000-0000-0000-0000-0000000000c2")
        val instanceRepo = InMemoryEventInstanceRepository()
        val resRepo = InMemoryReservationRepository()
        instanceRepo.create(lesson(lessonA, series = seriesId))
        instanceRepo.create(lesson(lessonB, series = seriesId))
        val enrollment = resRepo.save(reservation(Reference.Series(seriesId), "Kurzista"))

        val svc = service(resRepo = resRepo, instanceRepo = instanceRepo)
        svc.setAttendance(enrollment.id, lessonA, true)

        assertEquals(true, svc.getAttendance(lessonA).getOrNull()!!.entries.single().checkedIn)
        assertEquals(false, svc.getAttendance(lessonB).getOrNull()!!.entries.single().checkedIn)
    }
}
