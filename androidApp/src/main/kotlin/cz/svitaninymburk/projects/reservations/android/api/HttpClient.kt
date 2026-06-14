package cz.svitaninymburk.projects.reservations.android.api

import cz.svitaninymburk.projects.reservations.android.BuildConfig
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun provideHttpClient(): HttpClient = HttpClient(Android) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
    }
    defaultRequest {
        url("https://rezervace.svitaninymburk.cz")
    }
    if (BuildConfig.DEBUG) {
        install(BenchmarkPlugin)
    }
}
