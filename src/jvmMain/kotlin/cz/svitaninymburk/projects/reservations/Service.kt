package cz.svitaninymburk.projects.reservations

class PingService : IPingService {

    override suspend fun ping(message: String): String {
        println(message)
        return "Hello world from server!"
    }
}
