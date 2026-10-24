package cz.svitaninymburk.projects.reservations

import androidx.compose.runtime.staticCompositionLocalOf
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
    /** Stránky administrace. Jádro je obalí admin layoutem a pustí k nim jen admina. */
    val adminPages: List<AdminPage> = emptyList(),
    /** Položky v bočním menu administrace — na konci, nebo za položkou jádra podle [AdminNavLink.after]. */
    val adminNavLinks: List<AdminNavLink> = emptyList(),
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

/**
 * Stránka administrace na `/admin` + [path] (např. `/slots`). S [detail] má i podstránky
 * `/admin` + [path] + `/{id}`, kam se předá zbytek adresy jako id.
 */
class AdminPage(
    val path: String,
    val content: @Composable IComponent.(PageContext) -> Unit,
    val detail: (@Composable IComponent.(PageContext, String) -> Unit)? = null,
)

class AdminNavLink(
    /** Celá adresa, např. `/admin/slots`. */
    val path: String,
    /** Celá třída ikony včetně velikosti a barvy, stejně jako u položek jádra. */
    val iconClass: String,
    val label: () -> String,
    /**
     * Cesta položky jádra, za kterou se odkaz vloží (např. `/admin/events`). Bez ní, nebo když
     * taková položka není, jde odkaz na konec. Víc odkazů za stejnou položkou drží pořadí modulů.
     */
    val after: String? = null,
)

/** Moduly zapojené do aplikace — pro místa, kam se nedají snadno předat parametrem (admin menu). */
val LocalWebFeatures = staticCompositionLocalOf<List<WebFeature>> { emptyList() }

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
    /** Výrazná pilulka místo nenápadného odkazu — pro službu, kterou chceme návštěvníkům ukázat. */
    val emphasized: Boolean = false,
    /** Delší popisek, který hlavička ukáže místo [label], když se vejde (na telefonu nemusí). */
    val longLabel: (() -> String)? = null,
)
