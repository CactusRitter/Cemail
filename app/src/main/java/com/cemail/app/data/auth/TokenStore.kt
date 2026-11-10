package com.cemail.app.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** OAuth tokens of one account. */
data class Tokens(val refreshToken: String?, val accessToken: String?, val expiresAtMillis: Long)

/**
 * Keeps OAuth tokens encrypted with an AES-GCM key that lives in the Android Keystore
 * (it never leaves secure hardware). Tokens are never written in plain text nor logged.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokens", Context.MODE_PRIVATE)

    fun save(accountId: String, tokens: Tokens) {
        prefs.edit()
            .putString("$accountId.refresh", tokens.refreshToken?.let(::encrypt))
            .putString("$accountId.access", tokens.accessToken?.let(::encrypt))
            .putLong("$accountId.expires", tokens.expiresAtMillis)
            .apply()
    }

    fun load(accountId: String): Tokens? {
        val refresh = prefs.getString("$accountId.refresh", null)
        val access = prefs.getString("$accountId.access", null)
        if (refresh == null && access == null) return null
        return try {
            Tokens(refresh?.let(::decrypt), access?.let(::decrypt), prefs.getLong("$accountId.expires", 0L))
        } catch (_: Exception) {
            null // key lost or data corrupted: the account will ask to sign in again
        }
    }

    fun clear(accountId: String) {
        prefs.edit().remove("$accountId.refresh").remove("$accountId.access").remove("$accountId.expires").apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_SIZE))
        }
        return String(cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "cemail_tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
