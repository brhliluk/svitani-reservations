package cz.svitaninymburk.projects.reservations.android.repository.auth

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class AuthData(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userJson: String? = null,
)

object AuthDataSerializer : Serializer<AuthData> {
    override val defaultValue = AuthData()

    override suspend fun readFrom(input: InputStream): AuthData = try {
        Json.decodeFromString(AuthData.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) { throw CorruptionException("Cannot read auth data", e) }

    override suspend fun writeTo(t: AuthData, output: OutputStream) = withContext(Dispatchers.IO) {
        output.write(Json.encodeToString(AuthData.serializer(), t).encodeToByteArray())
    }
}
