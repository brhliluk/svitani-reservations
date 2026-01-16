package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass


val AppSerializersModule = SerializersModule {
    polymorphic(User::class) {
        subclass(User.Google::class)
        subclass(User.Email::class)
    }
}