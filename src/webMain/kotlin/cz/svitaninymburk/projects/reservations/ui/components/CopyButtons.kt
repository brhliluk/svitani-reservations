package cz.svitaninymburk.projects.reservations.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.copyToClipboard
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.span
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Zkopíruje text a na dvě vteřiny se přepne do stavu "zkopírováno" — tlačítko
 * si jen přečte [isCopied]. Opakovaný klik odpočet restartuje, aby fajfka
 * nezmizela dřív, než uživatel stihne kliknout podruhé.
 */
class CopyFlash(private val scope: CoroutineScope) {
    var isCopied by mutableStateOf(false); private set

    private var resetJob: Job? = null

    fun copy(value: String) {
        copyToClipboard(value)
        isCopied = true
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(2.seconds)
            isCopied = false
        }
    }
}

@Composable
fun rememberCopyFlash(): CopyFlash {
    val scope = rememberCoroutineScope()
    return remember(scope) { CopyFlash(scope) }
}

/**
 * Ikonové tlačítko "zkopírovat odkaz" v rohu karty akce i kurzu.
 * [label] je popisek pro čtečku i bublinu, po zkopírování ho vystřídá "Zkopírováno".
 */
@Composable
fun IComponent.CopyLinkButton(url: String, label: String, className: String? = null) {
    val currentStrings by strings
    val copyFlash = rememberCopyFlash()
    val stateClass = if (copyFlash.isCopied) "text-success" else "text-base-content/40 hover:text-base-content"

    button(className = listOfNotNull("btn btn-ghost btn-xs btn-square", className, "tooltip tooltip-left", stateClass).joinToString(" ")) {
        attribute("aria-label", label)
        attribute("data-tip", if (copyFlash.isCopied) currentStrings.copied else label)
        onClick { copyFlash.copy(url) }
        span(className = if (copyFlash.isCopied) "icon-[heroicons--check] size-4" else "icon-[heroicons--link] size-4")
    }
}
