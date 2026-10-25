package com.nightrider.driver

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Local settings for the driver app.
 *
 * The backend has no driver accounts: a driver is identified by the name they
 * type, and the app carries that name on every request. The password in the
 * sign-in card is therefore a local device PIN that locks the signed-in
 * session on this phone. It is stored only as a salted SHA-256 digest, never
 * in plain text, and can be reset from Settings if it is forgotten.
 */
class Prefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("nightrider_driver", Context.MODE_PRIVATE)

    /** The driver signed in right now. Empty when signed out. */
    var driverName: String
        get() = sp.getString(KEY_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_NAME, value).apply()

    /** Last name used, kept so the sign-in field is pre-filled after logout. */
    var lastName: String
        get() = sp.getString(KEY_LAST, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST, value).apply()

    var keepAwake: Boolean
        get() = sp.getBoolean(KEY_AWAKE, true)
        set(value) = sp.edit().putBoolean(KEY_AWAKE, value).apply()

    val loggedIn: Boolean
        get() = driverName.isNotBlank()

    fun hasPin(name: String): Boolean = sp.contains(pinKey(name))

    fun pinMatches(name: String, pin: String): Boolean =
        sp.getString(pinKey(name), null) == digest(name, pin)

    fun setPin(name: String, pin: String) {
        sp.edit().putString(pinKey(name), digest(name, pin)).apply()
    }

    fun clearPin(name: String) {
        if (name.isNotBlank()) sp.edit().remove(pinKey(name)).apply()
    }

    fun login(name: String) {
        lastName = name
        driverName = name
    }

    fun logout() {
        sp.edit().remove(KEY_NAME).apply()
    }

    private fun pinKey(name: String) = "pin_" + name.trim().lowercase()

    private fun digest(name: String, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((name.trim().lowercase() + ":" + pin).toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }

    companion object {
        /** The one server the app ever talks to. Not configurable at runtime. */
        const val SERVER = "https://nightrider.onrender.com"

        private const val KEY_NAME = "driver_name"
        private const val KEY_LAST = "last_name"
        private const val KEY_AWAKE = "keep_awake"
    }
}
