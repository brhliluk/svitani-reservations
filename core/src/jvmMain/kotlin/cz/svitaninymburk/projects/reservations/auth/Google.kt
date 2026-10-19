package cz.svitaninymburk.projects.reservations.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import java.util.Collections

data class GoogleUser(
    val email: String,
    val name: String,
    val surname: String,
    val googleSub: String // Unikátní ID
)

class GoogleAuthService(
    private val clientId: String = "VÁŠ_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
) {
    private val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory())
        .setAudience(Collections.singletonList(clientId))
        .build()

    fun verifyToken(tokenString: String): GoogleUser? {
        return try {
            val idToken = verifier.verify(tokenString) ?: return null
            val payload = idToken.payload

            GoogleUser(
                email = payload.email,
                name = payload["name"] as String,
                surname = payload["family_name"] as String,
                googleSub = payload.subject
            )
        } catch (e: Exception) {
            null // Logovat chybu
        }
    }
}