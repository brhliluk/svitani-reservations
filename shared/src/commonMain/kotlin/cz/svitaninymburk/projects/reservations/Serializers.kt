package cz.svitaninymburk.projects.reservations

import arrow.core.serialization.ArrowModule
import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass


val AppSerializersModule = SerializersModule {
    polymorphic(User::class) {
        subclass(User.Google::class)
        subclass(User.Email::class)
    }
}

val AppJson = Json {
    serializersModule = AppSerializersModule + ArrowModule
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

val RpcSerializersModules: List<SerializersModule> = listOf(
    ArrowModule,
    AppSerializersModule,
)