package cz.svitaninymburk.projects.reservations.ui.admin

import cz.svitaninymburk.projects.reservations.ui.admin.usecase.CapacityLevel
import cz.svitaninymburk.projects.reservations.ui.admin.usecase.capacityLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminDashboardUseCasesSpec {

    @Test
    fun emptyAndHalfFullLessonsAreFine() {
        assertEquals(CapacityLevel.OK, capacityLevel(occupied = 0, capacity = 10))
        assertEquals(CapacityLevel.OK, capacityLevel(occupied = 5, capacity = 10))
    }

    @Test
    fun theEightyPercentThresholdMustBeCrossedNotReached() {
        // 8 z 10 je přesně 0.8 — pořád v pohodě.
        assertEquals(CapacityLevel.OK, capacityLevel(occupied = 8, capacity = 10))
        assertEquals(CapacityLevel.NEARLY_FULL, capacityLevel(occupied = 9, capacity = 10))
    }

    @Test
    fun fullMeansOccupiedReachedCapacity() {
        assertEquals(CapacityLevel.FULL, capacityLevel(occupied = 10, capacity = 10))
    }

    @Test
    fun overbookedLessonReadsAsFullNotNearlyFull() {
        // Do lekce kurzu se počítají i účastníci kurzu, takže obsazeno může
        // převýšit kapacitu. Takový termín musí být červený, ne oranžový.
        assertEquals(CapacityLevel.FULL, capacityLevel(occupied = 11, capacity = 10))
        assertEquals(CapacityLevel.FULL, capacityLevel(occupied = 17, capacity = 10))
    }

    @Test
    fun zeroCapacityIsFullWithoutDividingByZero() {
        // Kapacita 0 se dřív dělila nulou; FULL je první větev, takže se tam
        // dělení vůbec nedostane.
        assertEquals(CapacityLevel.FULL, capacityLevel(occupied = 0, capacity = 0))
    }

    @Test
    fun smallLessonsCrossTheThresholdSooner() {
        // U třímístné lekce je 2/3 = 0.66 → OK, 3/3 → plno; oranžová fáze
        // u tak malé kapacity vůbec nenastane.
        assertEquals(CapacityLevel.OK, capacityLevel(occupied = 2, capacity = 3))
        assertEquals(CapacityLevel.FULL, capacityLevel(occupied = 3, capacity = 3))
        // U pětimístné už ano: 4/5 = 0.8 → OK, ale 9/10 by bylo NEARLY_FULL.
        assertEquals(CapacityLevel.OK, capacityLevel(occupied = 4, capacity = 5))
    }
}
