package cz.svitaninymburk.projects.reservations.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.span

/**
 * Stránkovací lišta "Předchozí — strana X z Y — Další".
 *
 * Byla rozepsaná v šesti kopiích (rozvrh, rezervace, peněženky, platby a v
 * seznamu akcí dvakrát) a všechny se lišily jen třídami. Sjednocené je i to,
 * co bylo v každé kopii zvlášť: krajní strany se nepřekročí, i když se na
 * zašedlé tlačítko podaří kliknout.
 *
 * @param className obal lišty — jednotlivé obrazovky si řídí vnější odsazení
 * @param compact menší varianta do vnořeného řádku tabulky
 */
@Composable
fun IComponent.Pagination(
    page: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    className: String = "flex items-center justify-center gap-4 mt-4",
    compact: Boolean = false,
) {
    val currentStrings by strings
    val buttonClass = if (compact) "btn btn-ghost btn-xs" else "btn btn-outline btn-sm"
    val labelClass = if (compact) "text-xs text-base-content/50" else "text-sm text-base-content/70"

    div(className = className) {
        button(className = buttonClass) {
            disabled(page == 0)
            onClick { if (page > 0) onPageChange(page - 1) }
            +currentStrings.paginationPrevious
        }
        span(className = labelClass) {
            +currentStrings.paginationPageOf(page + 1, totalPages)
        }
        button(className = buttonClass) {
            disabled(page >= totalPages - 1)
            onClick { if (page < totalPages - 1) onPageChange(page + 1) }
            +currentStrings.paginationNext
        }
    }
}
