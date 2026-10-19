package cz.svitaninymburk.projects.reservations.ui.admin.events

import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.admin.EventsPage
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.childrenByDefinition
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.definitionRows
import cz.svitaninymburk.projects.reservations.ui.util.pageCount
import cz.svitaninymburk.projects.reservations.ui.util.pageSlice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

private val DEF_A = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
private val DEF_B = Uuid.parse("00000000-0000-0000-0000-0000000000b2")

private fun item(id: String, title: String, def: Uuid? = null, isDefOnly: Boolean = false, dateInfo: String = "") =
    AdminEventListItem(
        id = Uuid.parse(id),
        definitionId = def,
        title = title,
        isSeries = false,
        dateInfo = dateInfo,
        capacity = 10,
        occupiedSpots = 0,
        priceString = "0",
        isDefinitionOnly = isDefOnly,
    )

private fun page(items: List<AdminEventListItem>, total: Long) = EventsPage(items, 0, 20, total)

class EventsListUseCasesSpec {

    @Test
    fun definitionRowsFiltersDefinitionOnlyAndSortsByTitle() {
        val p = page(
            listOf(
                item("00000000-0000-0000-0000-0000000000a1", "Zebra", isDefOnly = true),
                item("00000000-0000-0000-0000-0000000000b2", "Alfa", isDefOnly = true),
                item("00000000-0000-0000-0000-0000000000c3", "child", def = DEF_A, isDefOnly = false),
            ),
            total = 2,
        )
        assertEquals(listOf("Alfa", "Zebra"), definitionRows(p).map { it.title })
    }

    @Test
    fun childrenByDefinitionGroupsAndSortsByDateInfo() {
        val p = page(
            listOf(
                item("00000000-0000-0000-0000-0000000000a9", "Def A", isDefOnly = true),
                item("00000000-0000-0000-0000-0000000000c3", "c2", def = DEF_A, dateInfo = "2026-02-09"),
                item("00000000-0000-0000-0000-0000000000c4", "c1", def = DEF_A, dateInfo = "2026-02-02"),
                item("00000000-0000-0000-0000-0000000000c5", "b1", def = DEF_B, dateInfo = "2026-03-01"),
            ),
            total = 2,
        )
        val grouped = childrenByDefinition(p)
        assertEquals(listOf("c1", "c2"), grouped[DEF_A]!!.map { it.title })   // sorted by dateInfo
        assertEquals(listOf("b1"), grouped[DEF_B]!!.map { it.title })
        // definition-only rows are excluded from grouping entirely: "Def A" has a null
        // definitionId, so if the isDefinitionOnly filter regressed it would appear under
        // the null key — this assertion genuinely fails in that case.
        assertEquals(null, grouped[null])
        assertEquals(false, grouped.values.any { group -> group.any { it.title == "Def A" } })
    }

    @Test
    fun pageCountRoundsUpAndFloorsAtOne() {
        assertEquals(1, pageCount(0L, 20))
        assertEquals(1, pageCount(20L, 20))
        assertEquals(2, pageCount(21L, 20))
        assertEquals(3, pageCount(25L, 10))
    }

    @Test
    fun pageSliceReturnsThePageWindow() {
        val list = (1..25).toList()
        assertEquals((1..10).toList(), pageSlice(list, 0, 10))
        assertEquals((11..20).toList(), pageSlice(list, 1, 10))
        assertEquals((21..25).toList(), pageSlice(list, 2, 10))
        assertEquals(emptyList(), pageSlice(list, 3, 10))
    }
}
