package cz.svitaninymburk.projects.reservations.android.error

import android.content.Context
import cz.svitaninymburk.projects.reservations.android.R

sealed class RepositoryError {
    data object Network : RepositoryError()
    data object Unauthorized : RepositoryError()
    data class Http(val statusCode: Int) : RepositoryError()
    data object Parse : RepositoryError()
    data class Server(val code: String, val message: String) : RepositoryError()
}

fun RepositoryError.toMessage(context: Context): String = when (this) {
    is RepositoryError.Network -> context.getString(R.string.error_network)
    is RepositoryError.Unauthorized -> context.getString(R.string.error_unauthorized)
    is RepositoryError.Http -> context.getString(R.string.error_http, statusCode)
    is RepositoryError.Parse -> context.getString(R.string.error_parse)
    is RepositoryError.Server -> message
}
