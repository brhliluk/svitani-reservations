package cz.svitaninymburk.projects.reservations.repository

import cz.svitaninymburk.projects.reservations.repository.attendance.InMemoryAttendanceRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class InMemoryAttendanceRepositoryTest {
    @Test
    fun defaultsToNotCheckedIn() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        assertFalse(repo.isCheckedIn(Uuid.random()))
    }

    @Test
    fun setAndReadCheckedIn() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        val rid = Uuid.random()
        repo.setCheckedIn(rid, true)
        assertTrue(repo.isCheckedIn(rid))
        repo.setCheckedIn(rid, false)
        assertFalse(repo.isCheckedIn(rid))
    }

    @Test
    fun checkedInSetContainsOnlyCheckedReservations() = runBlocking {
        val repo = InMemoryAttendanceRepository()
        val a = Uuid.random(); val b = Uuid.random()
        repo.setCheckedIn(a, true)
        val flags = repo.checkedInFlags(listOf(a, b))
        assertEquals(true, flags[a])
        assertEquals(false, flags[b])
    }
}
