package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.mock.MockDataLoader
import cz.svitaninymburk.projects.reservations.plugins.configureDatabases
import cz.svitaninymburk.projects.reservations.plugins.startPaymentCheck
import cz.svitaninymburk.projects.reservations.plugins.configureRouting
import cz.svitaninymburk.projects.reservations.plugins.configureSecurity
import dev.kilua.rpc.initRpc
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.launch


fun Application.main() {
    install(Compression)
    install(WebSockets)
    configureDatabases()
    initRpc(
        initStaticResources = true,
        AppJson,
        appModule
    )
    startPaymentCheck()
    configureSecurity()
    configureRouting()

    if (System.getenv("LOAD_MOCK_DATA").toBoolean()) {
        val mockLoader = MockDataLoader()
        launch {
            mockLoader.clearAll()
            mockLoader.load()
            println("Mock data pro Rodinné centrum načtena ✅")
        }
    }
}
