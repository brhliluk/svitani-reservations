package cz.svitaninymburk.projects.reservations.ui.admin.schedule

import cz.svitaninymburk.projects.reservations.ui.admin.schedule.usecase.firstUpcomingPage
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleUseCasesSpec {

    @Test
    fun firstUpcomingPageFloorsToThePageHoldingTheFirstUpcomingItem() {
        assertEquals(0, firstUpcomingPage(0L, 20))
        assertEquals(0, firstUpcomingPage(19L, 20))
        // 20 minulých termínů zabere celou první stránku, nadcházející začínají na druhé.
        assertEquals(1, firstUpcomingPage(20L, 20))
        assertEquals(2, firstUpcomingPage(45L, 20))
    }

    @Test
    fun firstUpcomingPageSurvivesNonsensePageSize() {
        assertEquals(0, firstUpcomingPage(100L, 0))
    }
}
