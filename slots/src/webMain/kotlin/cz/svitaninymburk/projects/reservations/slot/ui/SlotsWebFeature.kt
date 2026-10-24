package cz.svitaninymburk.projects.reservations.slot.ui

import cz.svitaninymburk.projects.reservations.AdminNavLink
import cz.svitaninymburk.projects.reservations.AdminPage
import cz.svitaninymburk.projects.reservations.HeaderLink
import cz.svitaninymburk.projects.reservations.WebFeature
import cz.svitaninymburk.projects.reservations.WebPage
import cz.svitaninymburk.projects.reservations.slot.ui.admin.ADMIN_SLOTS_PATH
import cz.svitaninymburk.projects.reservations.slot.ui.admin.AdminSlotServiceScreen
import cz.svitaninymburk.projects.reservations.slot.ui.admin.AdminSlotServicesScreen
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotAdminStrings
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotStrings

/** Cesta veřejné stránky hlídání. */
const val SLOTS_PATH = "/hlidani"

/** Hlídání dětí, jak ho do webu zapojí aplikace (`Features.kt`). */
val slotsWebFeature = WebFeature(
    name = "slots",
    publicPages = listOf(WebPage(SLOTS_PATH) { context -> SlotPage(context) }),
    headerLinks = listOf(
        HeaderLink(
            path = SLOTS_PATH,
            // Stejná ikona jako v admin menu; hodiny četly spíš jako otevírací dobu.
            iconClass = "icon-[heroicons--face-smile]",
            label = { slotStrings.navLink },
            // Nepublikovanou službu vidí jen admin, takže host odkaz do té doby nevidí.
            isVisible = { slotService().listServices().isNotEmpty() },
            emphasized = true,
            longLabel = { slotStrings.navLinkLong },
        ),
    ),
    adminPages = listOf(
        AdminPage(
            path = "/slots",
            content = { AdminSlotServicesScreen() },
            detail = { _, id -> AdminSlotServiceScreen(id) },
        ),
    ),
    adminNavLinks = listOf(
        // V menu hned pod „Události a kurzy“, ne až na konci za nastavením.
        AdminNavLink(
            path = ADMIN_SLOTS_PATH,
            iconClass = "icon-[heroicons--face-smile] size-5 text-primary/70",
            label = { slotAdminStrings.navLabel },
            after = "/admin/events",
        ),
    ),
)
