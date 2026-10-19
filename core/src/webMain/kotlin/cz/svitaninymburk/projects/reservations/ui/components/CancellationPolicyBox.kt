package cz.svitaninymburk.projects.reservations.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.li
import dev.kilua.html.span
import dev.kilua.html.ul

@Composable
fun IComponent.CancellationPolicyBox() {
    var expanded by remember { mutableStateOf(false) }
    val currentStrings by strings

    div(className = "rounded-xl overflow-hidden border border-base-300") {
        button(className = "w-full text-left px-4 py-3 font-medium flex items-center justify-between gap-2 bg-base-200/60 hover:bg-base-200 transition-colors") {
            onClick { expanded = !expanded }
            div(className = "flex items-center gap-2 text-sm") {
                span(className = "icon-[heroicons--information-circle] size-4 text-info flex-shrink-0")
                span { +currentStrings.cancellationPolicyTitle }
            }
            span(className = if (expanded) "icon-[heroicons--chevron-up] size-4 text-base-content/50"
                             else           "icon-[heroicons--chevron-down] size-4 text-base-content/50")
        }
        if (expanded) {
            div(className = "px-4 py-3 bg-base-100") {
                ul(className = "space-y-1.5 text-sm text-base-content/70") {
                    li { +currentStrings.cancellationPolicyDeadline }
                    li { +currentStrings.cancellationPolicyLessonOptOut }
                    li { +currentStrings.cancellationPolicyNoRefundUnpaid }
                    li { +currentStrings.cancellationPolicyNoRefundLate }
                    li { +currentStrings.cancellationPolicyWalletExplain }
                }
            }
        }
    }
}
