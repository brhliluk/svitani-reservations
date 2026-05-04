package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.h2
import dev.kilua.html.p

@Composable
fun IComponent.PrivacyScreen() {
    val strings by strings

    div(className = "max-w-3xl mx-auto px-4 py-10") {
        h1(className = "text-2xl font-bold mb-8") { +strings.privacyTitle }

        PrivacySection(strings.privacyControllerHeading, strings.privacyControllerBody)
        PrivacySection(strings.privacyDataHeading, strings.privacyDataBody)
        PrivacySection(strings.privacyPurposeHeading, strings.privacyPurposeBody)
        PrivacySection(strings.privacyRecipientsHeading, strings.privacyRecipientsBody)
        PrivacySection(strings.privacyRetentionHeading, strings.privacyRetentionBody)
        PrivacySection(strings.privacyRightsHeading, strings.privacyRightsBody)
        PrivacySection(strings.privacyContactHeading, strings.privacyContactBody)
    }
}

@Composable
private fun IComponent.PrivacySection(heading: String, body: String) {
    div(className = "mb-6") {
        h2(className = "text-lg font-semibold mb-2") { +heading }
        p(className = "text-base-content/80 leading-relaxed") { +body }
    }
}
