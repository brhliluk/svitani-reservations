package cz.svitaninymburk.projects.reservations

import androidx.compose.runtime.Composable
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent

/**
 * Volitelný modul aplikace na straně webu (např. hlídání dětí). Protějšek [ServerFeature]:
 * jádro o modulech neví, aplikace mu jejich seznam předá do `MainLayout` a jádro je jen zapojí.
 */
class WebFeature(
    val name: String,
    /** Veřejné stránky modulu. Jádro je obalí hlavičkou a patičkou jako vlastní veřejné stránky. */
    val publicPages: List<WebPage> = emptyList(),
    /** Odkazy v hlavičce vedle „Přehled“. */
    val headerLinks: List<HeaderLink> = emptyList(),
)

class WebPage(
    /** Cesta bez parametrů, např. `/hlidani`. */
    val path: String,
    val content: @Composable IComponent.(PageContext) -> Unit,
)

/** Co stránka modulu ví o návštěvníkovi. */
class PageContext(
    val user: User?,
    /** Peněženka přihlášeného uživatele, pro předvyplnění rezervačního formuláře. */
    val walletCode: String?,
)

class HeaderLink(
    val path: String,
    /** Celá třída ikony, např. `icon-[heroicons--clock]` — Tailwind skládané třídy nenajde. */
    val iconClass: String,
    /** Volá se při vykreslení, takže text sleduje přepnutý jazyk. */
    val label: () -> String,
    /**
     * Zda odkaz ukázat. Jádro se ptá znovu po přihlášení i odhlášení, protože
     * admin může vidět víc než host. Výjimka znamená odkaz skrýt.
     */
    val isVisible: suspend () -> Boolean = { true },
)
