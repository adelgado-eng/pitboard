package com.pitboard.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 04/09/2026: sustituye la passphrase de SQLCipher hardcodeada ("pitboard-secure-key-2026",
 * extraíble trivialmente de un APK descompilado) por una passphrase aleatoria de 256 bits
 * generada en el primer arranque. Esa passphrase se guarda cifrada en SharedPreferences con
 * una clave AES-256/GCM que vive dentro del Android Keystore (respaldada por hardware —
 * StrongBox/TEE — cuando el dispositivo lo soporta) y que nunca sale de él en texto plano:
 * solo se usa para envolver/desenvolver la passphrase real, nunca para cifrar la BD directamente
 * (SQLCipher necesita los bytes de la passphrase, no una operación de Keystore).
 */
object DatabasePassphraseProvider {
    private const val KEYSTORE_ALIAS = "pitboard_db_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "pitboard_db_prefs"
    private const val PREF_PASSPHRASE = "encrypted_passphrase"
    private const val PREF_IV = "passphrase_iv"
    private const val PASSPHRASE_BYTES = 32
    private const val GCM_TAG_BITS = 128

    /** [isNewlyGenerated] es true solo la primera vez que se llama en un dispositivo dado. */
    data class Result(val passphrase: ByteArray, val isNewlyGenerated: Boolean)

    fun getOrCreatePassphrase(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedPassphrase = prefs.getString(PREF_PASSPHRASE, null)
        val storedIv = prefs.getString(PREF_IV, null)
        val keystoreKey = getOrCreateKeystoreKey()

        if (storedPassphrase != null && storedIv != null) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(storedIv, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val passphrase = cipher.doFinal(Base64.decode(storedPassphrase, Base64.NO_WRAP))
            return Result(passphrase, isNewlyGenerated = false)
        }

        val newPassphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)
        val encrypted = cipher.doFinal(newPassphrase)

        prefs.edit()
            .putString(PREF_PASSPHRASE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()

        return Result(newPassphrase, isNewlyGenerated = true)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
