package cz.svitaninymburk.projects.reservations.slot.ui

import cz.svitaninymburk.projects.reservations.HeaderLink
import cz.svitaninymburk.projects.reservations.WebFeature
import cz.svitaninymburk.projects.reservations.WebPage
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
            iconClass = "icon-[heroicons--clock]",
            label = { slotStrings.navLink },
            // Nepublikovanou službu vidí jen admin, takže host odkaz do té doby nevidí.
            isVisible = { slotService().listServices().isNotEmpty() },
        ),
    ),
)
