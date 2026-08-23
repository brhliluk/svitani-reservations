package cz.svitaninymburk.projects.reservations.repository

import cz.svitaninymburk.projects.reservations.repository.attendance.InMemoryAttendanceRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class InMemoryAttendanceRepositoryTest {

    private val lekceA = Uuid.parse("00000000-0000-0000-0000-0000000000c1")
    private val lekceB = Uuid.parse("00000000-0000-0000-0000-0000000000c2")

    @Test
    fun defaultsToNotCheckedIn() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        assertFalse(repo.isCheckedIn(Uuid.random(), lekceA))
    }

    @Test
    fun setAndReadCheckedIn() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        val rid = Uuid.random()
        repo.setCheckedIn(rid, lekceA, true)
        assertTrue(repo.isCheckedIn(rid, lekceA))
        repo.setCheckedIn(rid, lekceA, false)
        assertFalse(repo.isCheckedIn(rid, lekceA))
    }

    @Test
    fun checkedInSetContainsOnlyCheckedReservations() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        val a = Uuid.random(); val b = Uuid.random()
        repo.setCheckedIn(a, lekceA, true)
        val flags = repo.checkedInFlags(lekceA, listOf(a, b))
        assertEquals(true, flags[a])
        assertEquals(false, flags[b])
    }

    @Test
    fun `odskrtnuti na jedne lekci neoznaci druhou`() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        val rezervace = Uuid.parse("00000000-0000-0000-0000-0000000000d1")

        repo.setCheckedIn(rezervace, lekceA, true)

        assertTrue(repo.isCheckedIn(rezervace, lekceA))
        assertFalse(repo.isCheckedIn(rezervace, lekceB), "účastník kurzu je odškrtnutý jen na lekci A")
        assertEquals(mapOf(rezervace to false), repo.checkedInFlags(lekceB, listOf(rezervace)))
    }
}
