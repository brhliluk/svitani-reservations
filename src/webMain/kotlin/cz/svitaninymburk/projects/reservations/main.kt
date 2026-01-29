package cz.svitaninymburk.projects.reservations

import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.debug.randomDefinitionList
import cz.svitaninymburk.projects.reservations.debug.randomEventList
import cz.svitaninymburk.projects.reservations.debug.randomSeriesList
import cz.svitaninymburk.projects.reservations.i18n.detectLanguage
import cz.svitaninymburk.projects.reservations.ui.DashboardScreen
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationDetailScreen
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
import web.blob.Blob
import web.navigator.navigator

val AppScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

class App : Application() {
    override fun start() {
        detectLanguage(navigator.language)
        root("root") {
            browserRouter {
                route("/") {
                    view {
                        val router = Router.current
                        DashboardScreen(
                            user = null,
                            randomEventList,
                            randomSeriesList,
                            randomDefinitionList,
                            null,
                            { target, formData ->
                                // TODO: request
                                router.navigate("/reservation/${target.id}")
                            },
                        )
                    }
                }
                route("/reservation") {
                    string { reservationId -> view {
                        val router = Router.current
                        ReservationDetailScreen(
                            reservationId = reservationId.value,
                            onBackClick = {
                                router.navigate("/")
                            }
                        )
                    } }
                }
            }
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

expect fun copyToClipboard(text: String)

expect fun shareQrImage(imageUrl: String)

expect fun shareSvgAsPng(svgString: String)

expect fun shareBlob(blob: Blob)
