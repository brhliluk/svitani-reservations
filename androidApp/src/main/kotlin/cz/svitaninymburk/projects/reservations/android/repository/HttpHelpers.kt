package cz.svitaninymburk.projects.reservations.android.repository

import android.content.SharedPreferences
import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.api.ApiError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Shared HTTP plumbing for all repositories: bearer auth, status checking, [ApiError]
 * mapping and body parsing — so each repository method is a one-line delegation.
 */

/** The stored bearer access token, or null when the user is not signed in. */
internal val SharedPreferences.accessToken: String?
    get() = getString("access_token", null)

/** Authenticated GET that parses the JSON response into [T]. */
internal suspend inline fun <reified T> HttpClient.authGet(
    path: String,
    token: String?,
): Either<RepositoryError, T> = either {
    ensure(token != null) { RepositoryError.Unauthorized }
    val response = catch { get(path) { bearerAuth(token) } }
        .mapLeft { RepositoryError.Network }.bind()
    ensure(response.status.isSuccess()) { response.toRepositoryError() }
    catch { response.body<T>() }.mapLeft { RepositoryError.Parse }.bind()
}

/** Authenticated POST with a JSON [body] that parses the JSON response into [T]. */
internal suspend inline fun <reified B, reified T> HttpClient.authPost(
    path: String,
    token: String?,
    body: B,
): Either<RepositoryError, T> = either {
    ensure(token != null) { RepositoryError.Unauthorized }
    val response = catch {
        post(path) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.mapLeft { RepositoryError.Network }.bind()
    ensure(response.status.isSuccess()) { response.toRepositoryError() }
    catch { response.body<T>() }.mapLeft { RepositoryError.Parse }.bind()
}

/** Authenticated POST with no request body and no parsed response. */
internal suspend fun HttpClient.authPostNoBody(
    path: String,
    token: String?,
): Either<RepositoryError, Unit> = either {
    ensure(token != null) { RepositoryError.Unauthorized }
    val response = catch { post(path) { bearerAuth(token) } }
        .mapLeft { RepositoryError.Network }.bind()
    ensure(response.status.isSuccess()) { response.toRepositoryError() }
}

/** Unauthenticated POST with a JSON [body] that parses the JSON response into [T] (e.g. login). */
internal suspend inline fun <reified B, reified T> HttpClient.postJson(
    path: String,
    body: B,
): Either<RepositoryError, T> = either {
    val response = catch {
        post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.mapLeft { RepositoryError.Network }.bind()
    ensure(response.status.isSuccess()) { response.toRepositoryError() }
    catch { response.body<T>() }.mapLeft { RepositoryError.Parse }.bind()
}

/** Maps a failed HTTP response to a Server error (from the ApiError body) or a bare Http error. */
internal suspend fun HttpResponse.toRepositoryError(): RepositoryError =
    catch { body<ApiError>() }
        .map { RepositoryError.Server(it.code, it.message) }
        .getOrElse { RepositoryError.Http(status.value) }
