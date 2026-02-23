package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.mock.MockDataLoader
import cz.svitaninymburk.projects.reservations.plugins.configureDatabases
import cz.svitaninymburk.projects.reservations.plugins.startPaymentCheck
import cz.svitaninymburk.projects.reservations.plugins.configureRouting
import cz.svitaninymburk.projects.reservations.plugins.configureSecurity
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import dev.kilua.rpc.initRpc
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject


fun Application.main() {
    install(Compression)
    install(WebSockets)
    configureDatabases()
    initRpc(
        initStaticResources = false,
        AppJson,
        appModule
    )
    startPaymentCheck()
    configureSecurity()
    configureRouting()

    val mockLoader = MockDataLoader()


    launch {
        mockLoader.clearAll()
        mockLoader.load()
        println("Mock data pro Rodinné centrum načtena ✅")
    }
}
