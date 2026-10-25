package com.nightrider.driver

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import org.json.JSONObject

/**
 * Settings: who is signed in, the fixed server address, screen-awake, and the
 * only logout control in the app.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        val driverValue = findViewById<TextView>(R.id.driverValue)
        val serverValue = findViewById<TextView>(R.id.serverValue)
        val keepAwake = findViewById<CheckBox>(R.id.keepAwake)
        val logout = findViewById<Button>(R.id.logoutButton)
        val close = findViewById<Button>(R.id.closeButton)

        serverValue.text = Prefs.SERVER
        keepAwake.isChecked = prefs.keepAwake
        keepAwake.setOnCheckedChangeListener { _, checked -> prefs.keepAwake = checked }

        val name = prefs.driverName
        driverValue.text = if (name.isBlank()) getString(R.string.not_signed_in) else name
        logout.isEnabled = prefs.loggedIn

        logout.setOnClickListener {
            confirm(getString(R.string.logout_confirm), getString(R.string.action_logout)) {
                doLogout()
            }
        }
        close.setOnClickListener { finish() }
    }

    private fun doLogout() {
        val name = prefs.username
        if (name.isNotBlank()) prefs.lastName = name
        val token = prefs.sessionToken
        Thread {
            Api.sessionToken = token
            try {
                if (token.isNotBlank()) Api.post("/api/auth/logout", JSONObject())
            } catch (_: Exception) {
                // A local sign-out must still work when the server is unreachable.
            } finally {
                Api.sessionToken = ""
                prefs.logout()
                runOnUiThread { finish() }
            }
        }.start()
    }

    private fun confirm(message: String, positive: String, run: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(positive) { _, _ -> run() }
            .show()
    }
}

