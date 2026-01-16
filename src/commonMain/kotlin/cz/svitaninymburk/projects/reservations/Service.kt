package cz.svitaninymburk.projects.reservations

import dev.kilua.rpc.annotations.RpcService

@RpcService
interface IPingService {
    suspend fun ping(message: String): String
}
