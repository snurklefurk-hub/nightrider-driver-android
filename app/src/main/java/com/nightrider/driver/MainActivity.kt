package com.nightrider.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * The driver app.
 *
 * It loads the live NightRider driver page from your own server, so the app and
 * the website are the same experience: the ride workflow (head to pickup, arrive
 * at pickup, head to destination, arrive at destination, complete ride), the open
 * requests board, rider ratings, the shared team text with the Private toggle,
 * the nightwave styling, and the driver check-in all come from the same page.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var splash: View
    private lateinit var errorPanel: View
    private lateinit var errorText: TextView

    private lateinit var prefs: Prefs
    private var loadedUrl: String? = null

    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null

    private val geoPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allowed = grants.values.any { it }
        geoCallback?.invoke(geoOrigin, allowed, false)
        geoCallback = null
        geoOrigin = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)

        web = findViewById(R.id.web)
        swipe = findViewById(R.id.swipe)
        splash = findViewById(R.id.splash)
        errorPanel = findViewById(R.id.errorPanel)
        errorText = findViewById(R.id.errorText)

        findViewById<Button>(R.id.retry).setOnClickListener {
            errorPanel.visibility = View.GONE
            splash.visibility = View.VISIBLE
            loadDriverPage()
        }
        findViewById<Button>(R.id.changeServer).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.cyan))
        swipe.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.indigo))
        swipe.setOnRefreshListener { web.reload() }

        configureWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (!prefs.hasServer()) {
            startActivity(Intent(this, SetupActivity::class.java))
        } else {
            loadDriverPage()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            // The team text cue must be able to play without an extra tap.
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString + " NightRiderDriverApp/1.0"
        }

        web.setBackgroundColor(ContextCompat.getColor(this, R.color.void_night))
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val host = url.host ?: return false
                // Your own pages stay in the app; anything else opens in the browser.
                if (host.equals(hostOf(prefs.serverUrl), ignoreCase = true)) return false
                return openOutside(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                swipe.isRefreshing = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                splash.visibility = View.GONE
                swipe.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) showError()
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel()
                showError()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            @Suppress("DEPRECATION")
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false)
                    return
                }
                geoCallback = callback
                geoOrigin = origin
                geoPermission.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun hostOf(url: String?): String? =
        try {
            Uri.parse(url ?: return null).host
        } catch (e: Exception) {
            null
        }

    private fun openOutside(url: Uri): Boolean =
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
            true
        } catch (e: Exception) {
            true
        }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun loadDriverPage() {
        val base = prefs.serverUrl
        if (base.isNullOrBlank()) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        loadedUrl = base
        errorPanel.visibility = View.GONE
        web.loadUrl("$base/driver.html")
    }

    private fun showError() {
        splash.visibility = View.GONE
        swipe.isRefreshing = false
        errorPanel.visibility = View.VISIBLE
        errorText.text = getString(R.string.error_body, prefs.serverUrl ?: "")
    }

    private fun applyKeepAwake() {
        if (prefs.keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onResume() {
        super.onResume()
        applyKeepAwake()
        // Timers keep running so the driver stays checked in with dispatch.
        web.resumeTimers()
        val base = prefs.serverUrl
        if (base.isNullOrBlank()) {
            if (loadedUrl != null) startActivity(Intent(this, SetupActivity::class.java))
        } else if (base != loadedUrl) {
            splash.visibility = View.VISIBLE
            loadDriverPage()
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        super.onDestroy()
        web.destroy()
    }
}
