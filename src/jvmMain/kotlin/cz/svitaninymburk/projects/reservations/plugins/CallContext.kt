package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.util.asContextElement
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.withContext

val CallContextPlugin = object : Plugin<ApplicationCallPipeline, Unit, Unit> {
    override val key = AttributeKey<Unit>("CallContextPlugin")

    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Call) {
            withContext(call.asContextElement()) {
                proceed()
            }
        }
    }
}