package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.debug.randomEventList
import cz.svitaninymburk.projects.reservations.i18n.detectLanguage
import cz.svitaninymburk.projects.reservations.ui.DashboardScreen
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
import dev.kilua.routing.browserRouter
import dev.kilua.startApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import web.navigator.navigator

val AppScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

class App : Application() {
    override fun start() {
        detectLanguage(navigator.language)
        root("root") {
            browserRouter {
                route("/debug") {
                    view { DashboardScreen(user = null, randomEventList, {}) }
                }
            }
//            ssrRouter {
//                route("/") {
//                    view {
//                        div {
//                            +i18n.tr("This is a localized message.")
//                        }
//                    }
//                }
//            }
        }
//        val pingService = getService<IPingService>()
//        AppScope.launch {
//            val pingResult = pingService.ping("Hello world from client!")
//            println(pingResult)
//        }
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
