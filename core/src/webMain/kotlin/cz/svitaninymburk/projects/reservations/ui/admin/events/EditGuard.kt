package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.p
import dev.kilua.html.span
import web.history.history

/**
 * Dotazy, které úprava kurzu i termínu klade stejně: snížení kapacity pod počet
 * obsazených míst a skrytí akce, na kterou už někdo má rezervaci. Model dodá, jak
 * se ukládá a jak se mění zveřejnění; stav obou modálů drží tahle třída.
 */
class EditGuard(
    private val occupiedSpots: () -> Int,
    private val capacity: () -> Int,
    /** null = akce ještě není načtená. */
    private val isPublished: () -> Boolean?,
    private val save: () -> Unit,
    private val setPublished: (Boolean) -> Unit,
) {
    var showCapacityWarning by mutableStateOf(false); private set
    var showHideConfirm by mutableStateOf(false); private set

    fun requestSave() {
        if (capacity() < occupiedSpots()) showCapacityWarning = true else save()
    }

    fun confirmCapacityWarning() {
        showCapacityWarning = false
        save()
    }

    fun dismissCapacityWarning() {
        showCapacityWarning = false
    }

    fun requestTogglePublished() {
        val published = isPublished() ?: return
        if (published && occupiedSpots() > 0) showHideConfirm = true else setPublished(!published)
    }

    fun confirmHide() {
        showHideConfirm = false
        setPublished(false)
    }

    fun dismissHideConfirm() {
        showHideConfirm = false
    }
}

/** Záhlaví editace: zpět, nadpis, stav zveřejnění a přepínač (během ukládání zamčený). */
@Composable
fun IComponent.EditHeader(heading: String, title: String, isPublished: Boolean, isBusy: Boolean, guard: EditGuard) {
    val currentStrings by strings
    div(className = "flex items-center gap-4") {
        button(className = "btn btn-circle btn-ghost btn-sm") {
            span(className = "icon-[heroicons--arrow-left] size-5"); onClick { history.back() }
        }
        div(className = "flex-1") {
            h1(className = "text-3xl font-bold text-base-content") { +heading }
            p(className = "text-base-content/60") { +title }
        }
        if (isPublished) {
            span(className = "badge badge-primary badge-sm") { +currentStrings.statusPublished }
        } else {
            span(className = "badge badge-ghost badge-sm") { +currentStrings.statusHidden }
        }
        button(className = if (isPublished) "btn btn-sm btn-outline" else "btn btn-sm btn-primary") {
            disabled(isBusy)
            onClick { guard.requestTogglePublished() }
            +if (isPublished) currentStrings.hideButton else currentStrings.publishButton
        }
    }
}

@Composable
fun IComponent.EditGuardModals(guard: EditGuard) {
    val currentStrings by strings
    if (guard.showCapacityWarning) {
        ConfirmModal(
            title = currentStrings.capacityWarningTitle,
            confirmLabel = currentStrings.saveChanges,
            dismissLabel = currentStrings.cancel,
            isLoading = false,
            onConfirm = { guard.confirmCapacityWarning() },
            onDismiss = { guard.dismissCapacityWarning() },
            confirmClassName = "btn-warning",
        ) { p(className = "py-4") { +currentStrings.capacityWarningBody } }
    }
    if (guard.showHideConfirm) {
        ConfirmModal(
            title = currentStrings.hideButton,
            confirmLabel = currentStrings.hideButton,
            dismissLabel = currentStrings.cancel,
            isLoading = false,
            onConfirm = { guard.confirmHide() },
            onDismiss = { guard.dismissHideConfirm() },
            confirmClassName = "btn-warning",
        ) { p(className = "py-4") { +currentStrings.hideWithReservationsConfirm } }
    }
}
