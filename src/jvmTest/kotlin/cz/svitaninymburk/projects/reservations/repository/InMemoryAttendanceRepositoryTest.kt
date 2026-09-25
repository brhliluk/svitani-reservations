package cz.svitaninymburk.projects.reservations.repository

import cz.svitaninymburk.projects.reservations.repository.attendance.InMemoryAttendanceRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class InMemoryAttendanceRepositoryTest {

    private val lessonA = Uuid.parse("00000000-0000-0000-0000-0000000000c1")
    private val lessonB = Uuid.parse("00000000-0000-0000-0000-0000000000c2")

    @Test
    fun defaultsToNotCheckedIn() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        assertFalse(repo.isCheckedIn(Uuid.random(), lessonA))
    }

    @Test
    fun setAndReadCheckedIn() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        val rid = Uuid.random()
        repo.setCheckedIn(rid, lessonA, true)
        assertTrue(repo.isCheckedIn(rid, lessonA))
        repo.setCheckedIn(rid, lessonA, false)
        assertFalse(repo.isCheckedIn(rid, lessonA))
    }

    @Test
    fun checkedInSetContainsOnlyCheckedReservations() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        val a = Uuid.random(); val b = Uuid.random()
        repo.setCheckedIn(a, lessonA, true)
        val flags = repo.checkedInFlags(lessonA, listOf(a, b))
        assertEquals(true, flags[a])
        assertEquals(false, flags[b])
    }

    @Test
    fun `check-in on one lesson does not mark the other`() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        val reservation = Uuid.parse("00000000-0000-0000-0000-0000000000d1")

        repo.setCheckedIn(reservation, lessonA, true)

        assertTrue(repo.isCheckedIn(reservation, lessonA))
        assertFalse(repo.isCheckedIn(reservation, lessonB), "účastník kurzu je odškrtnutý jen na lekci A")
        assertEquals(mapOf(reservation to false), repo.checkedInFlags(lessonB, listOf(reservation)))
    }
}
