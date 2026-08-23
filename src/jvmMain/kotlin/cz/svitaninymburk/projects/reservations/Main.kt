package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.mock.MockDataLoader
import cz.svitaninymburk.projects.reservations.plugins.configureDatabases
import cz.svitaninymburk.projects.reservations.plugins.startPaymentCheck
import cz.svitaninymburk.projects.reservations.plugins.startWalletResetJobs
import cz.svitaninymburk.projects.reservations.plugins.startSeriesScheduleBackfill
import cz.svitaninymburk.projects.reservations.plugins.startOccupancyBackfill
import cz.svitaninymburk.projects.reservations.plugins.configureRouting
import cz.svitaninymburk.projects.reservations.plugins.configureSecurity
import cz.svitaninymburk.projects.reservations.plugins.configureSentry
import dev.kilua.rpc.initRpcKoin
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.websocket.*
import kotlinx.coroutines.launch


fun Application.main() {
    install(Compression)
    install(WebSockets)
    install(ContentNegotiation) {
        json(AppJson)
    }
    configureDatabases()
    initRpcKoin(initContentNegotiation = false) {
        modules(appModule)
    }
    startPaymentCheck()
    startWalletResetJobs()
    startSeriesScheduleBackfill()
    startOccupancyBackfill()
    configureSecurity()
    configureRouting()
    configureSentry()

    if (System.getenv("LOAD_MOCK_DATA").toBoolean()) {
        val mockLoader = MockDataLoader()
        launch {
            mockLoader.clearAll()
            mockLoader.load()
            println("Mock data pro Rodinné centrum načtena ✅")
        }
    }
}
