package cz.svitaninymburk.projects.reservations

import arrow.core.serialization.ArrowModule
import cz.svitaninymburk.projects.reservations.mock.MockDataLoader
import cz.svitaninymburk.projects.reservations.plugins.payment.startPaymentCheck
import cz.svitaninymburk.projects.reservations.plugins.routing.configureRouting
import cz.svitaninymburk.projects.reservations.plugins.security.configureSecurity
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import dev.kilua.rpc.initRpc
import dev.kilua.ssr.initSsr
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import org.koin.ktor.ext.inject


fun Application.main() {
    install(Compression)
    install(WebSockets)
    initRpc(
        initStaticResources = false,
        AppJson,
        appModule
    )
    startPaymentCheck()
    configureSecurity()
    configureRouting()
    initSsr()

    val mockLoader = MockDataLoader
    val repositories = object {
        val definition: EventDefinitionRepository by inject()
        val instance: EventInstanceRepository by inject()
        val series: EventSeriesRepository by inject()
    }

    launch {
        mockLoader.load(repositories.definition, repositories.instance, repositories.series)
        println("Mock data pro Rodinné centrum načtena ✅")
    }
}
