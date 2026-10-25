package com.nightrider.driver

import android.content.Context
import android.content.SharedPreferences

/**
 * Small settings store for the app.
 *
 * serverUrl   - the NightRider site address, e.g. https://nightrider-xxxx.onrender.com
 * keepAwake   - keep the screen on while the app is open (default on, for drivers)
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = sp.getString(KEY_SERVER, null)
        set(value) {
            sp.edit().putString(KEY_SERVER, value).apply()
        }

    var keepAwake: Boolean
        get() = sp.getBoolean(KEY_AWAKE, true)
        set(value) {
            sp.edit().putBoolean(KEY_AWAKE, value).apply()
        }

    fun hasServer(): Boolean = !serverUrl.isNullOrBlank()

    fun clearServer() {
        sp.edit().remove(KEY_SERVER).apply()
    }

    companion object {
        private const val FILE = "nightrider_driver"
        private const val KEY_SERVER = "server_url"
        private const val KEY_AWAKE = "keep_awake"

        /** Turns whatever the driver typed into a clean base address, or null. */
        fun normalize(raw: String?): String? {
            var s = raw?.trim().orEmpty()
            if (s.isEmpty()) return null
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
                s = "https://$s"
            }
            while (s.endsWith("/")) s = s.dropLast(1)
            return if (s.length <= "https://".length) null else s
        }
    }
}
