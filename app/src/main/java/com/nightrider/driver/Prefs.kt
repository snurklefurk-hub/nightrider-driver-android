package com.nightrider.driver

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local preferences plus an Android Keystore protected NightRider session. */
class Prefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("nightrider_driver", Context.MODE_PRIVATE)

    var driverName: String
        get() = sp.getString(KEY_NAME, "") ?: ""
        private set(value) = sp.edit().putString(KEY_NAME, value).apply()

    var username: String
        get() = sp.getString(KEY_USERNAME, "") ?: ""
        private set(value) = sp.edit().putString(KEY_USERNAME, value).apply()

    var lastName: String
        get() = sp.getString(KEY_LAST, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST, value).apply()

    var sessionToken: String
        get() = readToken()
        private set(value) = writeToken(value)

    var keepAwake: Boolean
        get() = sp.getBoolean(KEY_AWAKE, true)
        set(value) = sp.edit().putBoolean(KEY_AWAKE, value).apply()

    /** Device-local, opt-in appearance. Classic is preserved by default. */
    var aeroEnabled: Boolean
        get() = sp.getBoolean(KEY_AERO, false)
        set(value) = sp.edit().putBoolean(KEY_AERO, value).apply()

    val loggedIn: Boolean
        get() = driverName.isNotBlank() && sessionToken.isNotBlank()

    fun login(user: String, displayName: String, token: String) {
        lastName = user
        username = user
        driverName = displayName
        sessionToken = token
    }

    fun logout() {
        sp.edit().remove(KEY_NAME).remove(KEY_USERNAME).remove(KEY_TOKEN).apply()
    }

    private fun readToken(): String {
        val encoded = sp.getString(KEY_TOKEN, null) ?: return ""
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = 12
            if (packed.size <= ivSize) return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, packed, 0, ivSize))
            String(cipher.doFinal(packed.copyOfRange(ivSize, packed.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            sp.edit().remove(KEY_TOKEN).apply()
            ""
        }
    }

    private fun writeToken(token: String) {
        if (token.isBlank()) {
            sp.edit().remove(KEY_TOKEN).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        sp.edit().putString(KEY_TOKEN, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val SERVER = "https://nightrider.onrender.com"
        private const val KEY_ALIAS = "nightrider_session_key"
        private const val KEY_NAME = "driver_name"
        private const val KEY_USERNAME = "driver_username"
        private const val KEY_LAST = "last_username"
        private const val KEY_TOKEN = "session_token_encrypted"
        private const val KEY_AWAKE = "keep_awake"
        private const val KEY_AERO = "frutiger_aero_ui"
    }
}

