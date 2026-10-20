package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.ui.MainLayout
import dev.kilua.Application
import dev.kilua.CoreModule
import dev.kilua.TailwindcssModule
import dev.kilua.BootstrapIconsModule
import dev.kilua.TempusDominusModule
import dev.kilua.TomSelectModule
import dev.kilua.ImaskModule
import dev.kilua.AnimationModule
import dev.kilua.Hot
import dev.kilua.compose.root
import dev.kilua.startApplication
import cz.svitaninymburk.projects.reservations.i18n.strings
import web.dom.document

class App : Application() {
    override fun start() {
        // Bez souladu `lang` s obsahem nabízí Chrome (hlavně na mobilu) automatický překlad.
        document.documentElement.lang = strings.value.locale
        root("root") {
            MainLayout(webFeatures)
        }
    }
}

fun main() {
    startApplication(
        ::App,
        bundlerHot(),
        TailwindcssModule,
        BootstrapIconsModule,
        TempusDominusModule,
        TomSelectModule,
        ImaskModule,
        AnimationModule,
        CoreModule
    )
}

expect fun bundlerHot(): Hot?
