package com.nightrider.driver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

/**
 * One small settings screen: the NightRider address this app should open, and
 * whether the screen should stay awake while the app is open.
 *
 * Reachable on first launch, from the error screen, and by long-pressing the
 * app icon and choosing "Server settings".
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var urlField: EditText
    private lateinit var awakeBox: CheckBox
    private lateinit var status: TextView
    private lateinit var testButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        prefs = Prefs(this)

        urlField = findViewById(R.id.serverUrl)
        awakeBox = findViewById(R.id.keepAwake)
        status = findViewById(R.id.status)
        testButton = findViewById(R.id.test)

        urlField.setText(prefs.serverUrl.orEmpty())
        awakeBox.isChecked = prefs.keepAwake

        findViewById<Button>(R.id.save).setOnClickListener { save() }
        testButton.setOnClickListener { test() }
    }

    private fun save() {
        val clean = Prefs.normalize(urlField.text.toString())
        if (clean == null) {
            status.text = getString(R.string.setup_need_address)
            status.visibility = View.VISIBLE
            return
        }
        prefs.serverUrl = clean
        prefs.keepAwake = awakeBox.isChecked
        urlField.setText(clean)

        if (isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun test() {
        val clean = Prefs.normalize(urlField.text.toString())
        if (clean == null) {
            status.text = getString(R.string.setup_need_address)
            status.visibility = View.VISIBLE
            return
        }

        testButton.isEnabled = false
        status.visibility = View.VISIBLE
        status.text = getString(R.string.setup_testing)

        Thread {
            val result = ping(clean)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                testButton.isEnabled = true
                status.text = result
            }
        }.start()
    }

    /** Asks the server for /api/config, the same check the driver page makes. */
    private fun ping(base: String): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$base/api/config").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                getString(R.string.setup_ok)
            } else {
                getString(R.string.setup_bad_code, code)
            }
        } catch (e: Exception) {
            getString(R.string.setup_unreachable)
        } finally {
            try {
                conn?.disconnect()
            } catch (ignored: Exception) {
                // nothing to clean up
            }
        }
    }
}
