package cz.svitaninymburk.projects.reservations.util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SettingsEncryption {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    private fun keySpec(keyBase64: String): SecretKeySpec {
        val raw = Base64.getDecoder().decode(keyBase64)
        require(raw.size == 32) { "SETTINGS_ENCRYPTION_KEY must decode to exactly 32 bytes" }
        return SecretKeySpec(raw, "AES")
    }

    private fun envKey(): String =
        System.getenv("SETTINGS_ENCRYPTION_KEY")
            ?: error("SETTINGS_ENCRYPTION_KEY env var is required")

    fun encrypt(plaintext: String, keyBase64: String = envKey()): String {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(keyBase64), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        return "${enc.encodeToString(iv)}:${enc.encodeToString(ciphertext)}"
    }

    fun decrypt(stored: String, keyBase64: String = envKey()): String {
        val parts = stored.split(":")
        require(parts.size == 2) { "Invalid encrypted format — expected '<base64-iv>:<base64-ciphertext>'" }
        val dec = Base64.getDecoder()
        val iv = dec.decode(parts[0])
        val ciphertext = dec.decode(parts[1])
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec(keyBase64), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }
}
