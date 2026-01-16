package cz.svitaninymburk.projects.reservations

import arrow.core.serialization.ArrowModule
import cz.svitaninymburk.projects.reservations.plugins.payment.startPaymentCheck
import cz.svitaninymburk.projects.reservations.plugins.routing.configureRouting
import cz.svitaninymburk.projects.reservations.plugins.security.configureSecurity
import dev.kilua.rpc.initRpc
import dev.kilua.ssr.initSsr
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus


fun Application.main() {
    install(Compression)
    install(WebSockets)
    initRpc(
        initStaticResources = false,
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            serializersModule = AppSerializersModule + ArrowModule
        },
        appModule
    )
    startPaymentCheck()
    configureSecurity()
    configureRouting()
    initSsr()
}
