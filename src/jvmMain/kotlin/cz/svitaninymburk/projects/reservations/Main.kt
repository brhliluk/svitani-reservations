package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.mock.MockDataLoader
import cz.svitaninymburk.projects.reservations.plugins.configureDatabases
import cz.svitaninymburk.projects.reservations.plugins.startPaymentCheck
import cz.svitaninymburk.projects.reservations.plugins.startWalletResetJobs
import cz.svitaninymburk.projects.reservations.plugins.startSeriesScheduleBackfill
import cz.svitaninymburk.projects.reservations.plugins.configureRouting
import cz.svitaninymburk.projects.reservations.plugins.configureSecurity
import cz.svitaninymburk.projects.reservations.plugins.configureSentry
import dev.kilua.rpc.initRpcKoin
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.websocket.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import kotlinx.coroutines.launch
import org.slf4j.event.Level


fun Application.main() {
    install(Compression)
    // Bez tohoto nebylo v logu vidět, jak dlouho RPC volání trvalo ani jak skončilo —
    // zaseknutá rezervace se pak hledá jen v journalu podle stack trace.
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            val path = call.request.path()
            path.startsWith("/rpc") || path.startsWith("/api")
        }
        format { call ->
            val status = call.response.status()?.value?.toString() ?: "n/a"
            "${call.request.httpMethod.value} ${call.request.path()} -> $status in ${call.processingTimeMillis()}ms"
        }
    }
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
