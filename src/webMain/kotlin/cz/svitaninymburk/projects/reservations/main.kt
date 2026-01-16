package cz.svitaninymburk.projects.reservations

import dev.kilua.Application
import dev.kilua.CoreModule
import dev.kilua.TailwindcssModule
import dev.kilua.FontAwesomeModule
import dev.kilua.BootstrapIconsModule
import dev.kilua.TempusDominusModule
import dev.kilua.TomSelectModule
import dev.kilua.ImaskModule
import dev.kilua.AnimationModule
import dev.kilua.Hot
import dev.kilua.html.div
import dev.kilua.i18n.I18n
import dev.kilua.compose.root
import dev.kilua.rpc.getService
import dev.kilua.ssr.ssrRouter
import dev.kilua.startApplication
import kotlin.js.JsAny
import kotlin.js.JsModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val AppScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

@JsModule("./modules/i18n/messages-en.po")
external object messagesEn: JsAny

@JsModule("./modules/i18n/messages-pl.po")
external object messagesPl: JsAny

val i18n = I18n(
    "en" to messagesEn,
    "pl" to messagesPl
)

class App : Application() {
    override fun start() {
        root("root") {
            ssrRouter {
                route("/") {
                    view {
                        div {
                            +i18n.tr("This is a localized message.")
                        }
                    }
                }
            }
        }
        val pingService = getService<IPingService>()
        AppScope.launch {
            val pingResult = pingService.ping("Hello world from client!")
            println(pingResult)
        }
    }
}

fun main() {
    startApplication(
        ::App,
        bundlerHot(),
        TailwindcssModule,
        FontAwesomeModule,
        BootstrapIconsModule,
        TempusDominusModule,
        TomSelectModule,
        ImaskModule,
        AnimationModule,
        CoreModule
    )
}

expect fun bundlerHot(): Hot?
