package com.nightrider.driver

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** A non-2xx answer from the server, carrying the error text it sent. */
class ApiException(val status: Int, message: String) : Exception(message)

/**
 * Thin JSON client for the NightRider dispatch server.
 *
 * Every call is blocking, so callers run it on the app's single background
 * executor and hop back to the UI thread with the result.
 */
object Api {

    @Volatile var sessionToken: String = ""

    fun get(path: String): JSONObject = request("GET", path, null)

    fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    /** Revoke a captured token without restoring it to the global active session. */
    fun logout(token: String): JSONObject =
        request("POST", "/api/auth/logout", JSONObject(), tokenOverride = token)

    /** Percent-encode one path segment (driver names, ride ids). */
    fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun request(
        method: String, path: String, body: JSONObject?, tokenOverride: String? = null
    ): JSONObject {
        val conn = (URL(Prefs.SERVER + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NightRiderDriver/1.1 (Android)")
            val token = tokenOverride ?: sessionToken
            if (path != "/api/auth/login" && token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (method == "POST" && path == "/api/auth/login") {
                setRequestProperty("X-NightRider-Client", "android")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(s.reader(Charsets.UTF_8)).use { it.readText() }
            } ?: ""
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) {
                throw ApiException(code, json.optString("error").ifBlank { "Server error $code" })
            }
            return json
        } finally {
            conn.disconnect()
        }
    }
}

