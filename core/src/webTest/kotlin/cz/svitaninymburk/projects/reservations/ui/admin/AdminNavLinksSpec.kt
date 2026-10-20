package cz.svitaninymburk.projects.reservations.ui.admin

import cz.svitaninymburk.projects.reservations.AdminNavLink
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminNavLinksSpec {

    private fun link(path: String, after: String? = null) = AdminNavLink(path, "icon", { path }, after)

    private val core = listOf(link("/admin"), link("/admin/events"), link("/admin/settings"))

    private fun paths(links: List<AdminNavLink>) = links.map { it.path }

    @Test
    fun withoutFeatureLinksCoreOrderStays() {
        assertEquals(listOf("/admin", "/admin/events", "/admin/settings"), paths(mergeNavLinks(core, emptyList())))
    }

    @Test
    fun linkWithoutAnchorGoesToTheEnd() {
        val merged = mergeNavLinks(core, listOf(link("/admin/x")))
        assertEquals(listOf("/admin", "/admin/events", "/admin/settings", "/admin/x"), paths(merged))
    }

    @Test
    fun anchoredLinkGoesRightAfterItsCoreItem() {
        val merged = mergeNavLinks(core, listOf(link("/admin/slots", after = "/admin/events")))
        assertEquals(listOf("/admin", "/admin/events", "/admin/slots", "/admin/settings"), paths(merged))
    }

    @Test
    fun severalLinksAfterTheSameItemKeepTheirOrder() {
        val merged = mergeNavLinks(
            core,
            listOf(link("/admin/a", after = "/admin/events"), link("/admin/end"), link("/admin/b", after = "/admin/events")),
        )
        assertEquals(
            listOf("/admin", "/admin/events", "/admin/a", "/admin/b", "/admin/settings", "/admin/end"),
            paths(merged),
        )
    }

    @Test
    fun unknownAnchorFallsBackToTheEnd() {
        // Překlep nebo položka, kterou jádro zrušilo — odkaz nesmí zmizet z menu.
        val merged = mergeNavLinks(core, listOf(link("/admin/x", after = "/admin/missing")))
        assertEquals(listOf("/admin", "/admin/events", "/admin/settings", "/admin/x"), paths(merged))
    }
}
