package cz.svitaninymburk.projects.reservations.android.api

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey

object BenchmarkPlugin : HttpClientPlugin<Unit, BenchmarkPlugin> {
    override val key = AttributeKey<BenchmarkPlugin>("BenchmarkPlugin")

    override fun prepare(block: Unit.() -> Unit) = this

    override fun install(plugin: BenchmarkPlugin, scope: HttpClient) {
        scope.plugin(HttpSend).intercept { request ->
            val start = System.currentTimeMillis()
            val call = execute(request)
            val elapsed = System.currentTimeMillis() - start
            Log.d(
                "HTTP_BENCHMARK",
                "${request.method.value} ${request.url.encodedPath} → ${elapsed}ms [${call.response.status.value}]",
            )
            call
        }
    }
}
