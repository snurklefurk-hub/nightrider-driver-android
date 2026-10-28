package com.nightrider.driver

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/**
 * Settings: who is signed in, the fixed server address, screen-awake, and the
 * only logout control in the app.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var updateStatus: TextView
    private lateinit var checkUpdate: Button
    private lateinit var installUpdate: Button
    private var availableUpdate: InAppUpdates.Release? = null
    private var updateBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (prefs.aeroEnabled) setTheme(R.style.Theme_NightRider_Aero)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val settingsScroll = findViewById<ScrollView>(R.id.settingsScroll)
        if (prefs.aeroEnabled) {
            settingsScroll.setBackgroundResource(R.drawable.bg_aero_sky)
        } else {
            val settingsGrid = NightGridDrawable()
            settingsScroll.background = settingsGrid
            settingsScroll.setOnScrollChangeListener { _, _, y, _, _ ->
                settingsGrid.setParallaxOffset(y)
            }
        }

        val driverValue = findViewById<TextView>(R.id.driverValue)
        val serverValue = findViewById<TextView>(R.id.serverValue)
        val keepAwake = findViewById<CheckBox>(R.id.keepAwake)
        val logout = findViewById<Button>(R.id.logoutButton)
        val close = findViewById<Button>(R.id.closeButton)
        updateStatus = findViewById(R.id.updateStatus)
        checkUpdate = findViewById(R.id.checkUpdateButton)
        installUpdate = findViewById(R.id.installUpdateButton)
        // Keep the installed version visible even while an update check changes
        // the status text (including when offline or after a failed download).
        val installedInfo = packageManager.getPackageInfo(packageName, 0)
        findViewById<TextView>(R.id.currentVersionValue).text = getString(
            R.string.installed_version_value,
            installedInfo.versionName ?: "—",
            InAppUpdates.installedVersionCode(this)
        )
        checkUpdate.setOnClickListener { checkForUpdates() }
        installUpdate.setOnClickListener { installAvailableUpdate() }

        serverValue.text = Prefs.SERVER
        keepAwake.isChecked = prefs.keepAwake
        keepAwake.setOnCheckedChangeListener { _, checked -> prefs.keepAwake = checked }
        val appearance = findViewById<Button>(R.id.appearanceToggle)
        appearance.text = getString(if (prefs.aeroEnabled) R.string.mode_dark else R.string.mode_light)
        appearance.contentDescription = if (prefs.aeroEnabled) "Switch to dark mode" else "Switch to light mode"
        appearance.setOnClickListener {
            prefs.aeroEnabled = !prefs.aeroEnabled
            recreate()
        }

        val name = prefs.driverName
        driverValue.text = if (name.isBlank()) getString(R.string.not_signed_in) else name
        logout.isEnabled = prefs.loggedIn

        logout.setOnClickListener {
            confirm(getString(R.string.logout_confirm), getString(R.string.action_logout)) {
                doLogout()
            }
        }
        close.setOnClickListener { finish() }
        findViewById<Button>(R.id.velaNavTest).setOnClickListener {
            startActivity(Intent(this, VelaNavigationActivity::class.java).putExtra("test", true))
        }
        findViewById<Button>(R.id.mapSimulator).setOnClickListener {
            startActivity(Intent(this, DriverMapActivity::class.java).putExtra("simulation", true))
        }
        findViewById<Button>(R.id.mapFieldTest).setOnClickListener {
            startActivity(Intent(this, DriverMapActivity::class.java).putExtra("field_test", true))
        }
        checkForUpdates()
    }

    private fun checkForUpdates() {
        if (updateBusy) return
        updateBusy = true
        checkUpdate.isEnabled = false
        installUpdate.visibility = View.GONE
        updateStatus.text = getString(R.string.update_checking)
        Thread {
            try {
                val release = InAppUpdates.findUpdate(this)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    availableUpdate = release
                    updateStatus.text = if (release == null) getString(R.string.update_none) else
                        getString(R.string.update_available, release.versionCode)
                    installUpdate.visibility = if (release == null) View.GONE else View.VISIBLE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        updateStatus.text = getString(R.string.update_check_failed, e.message ?: "Connection unavailable")
                    }
                }
            } finally {
                runOnUiThread {
                    updateBusy = false
                    checkUpdate.isEnabled = true
                }
            }
        }.start()
    }

    private fun installAvailableUpdate() {
        val release = availableUpdate ?: return
        if (updateBusy) return
        if (!packageManager.canRequestPackageInstalls()) {
            updateStatus.text = getString(R.string.update_permission_help)
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")))
            return
        }
        updateBusy = true
        checkUpdate.isEnabled = false
        installUpdate.isEnabled = false
        updateStatus.text = getString(R.string.update_downloading)
        Thread {
            try {
                val apk = InAppUpdates.download(this, release)
                InAppUpdates.install(this, apk, release)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) updateStatus.text = getString(R.string.update_install_prompt)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        updateStatus.text = getString(R.string.update_install_failed, e.message ?: "Try again")
                    }
                }
            } finally {
                runOnUiThread {
                    updateBusy = false
                    checkUpdate.isEnabled = true
                    installUpdate.isEnabled = true
                }
            }
        }.start()
    }

    private fun doLogout() {
        val name = prefs.username
        if (name.isNotBlank()) prefs.lastName = name
        val token = prefs.sessionToken
        // Clear local credentials immediately, even without a network connection.
        prefs.logout()
        Api.sessionToken = ""
        finish()
        if (token.isNotBlank()) {
            Thread {
                try { Api.logout(token) } catch (_: Exception) {
                    // The server-side token expires independently if offline.
                }
            }.start()
        }
    }

    private fun confirm(message: String, positive: String, run: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(positive) { _, _ -> run() }
            .show()
    }
}
