package cz.svitaninymburk.projects.reservations.ui.admin.events

import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.ATTENDANCE_MAX_EXTRA_ROWS
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.clampExtraRows
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceUseCasesSpec {

    @Test
    fun extraRowsNeverGoBelowZero() {
        assertEquals(0, clampExtraRows(-1))
        assertEquals(0, clampExtraRows(0))
    }

    @Test
    fun extraRowsStopAtTheMaximum() {
        assertEquals(ATTENDANCE_MAX_EXTRA_ROWS, clampExtraRows(ATTENDANCE_MAX_EXTRA_ROWS + 1))
        assertEquals(ATTENDANCE_MAX_EXTRA_ROWS, clampExtraRows(999))
    }

    @Test
    fun extraRowsInRangePassThrough() {
        assertEquals(5, clampExtraRows(5))
    }
}
