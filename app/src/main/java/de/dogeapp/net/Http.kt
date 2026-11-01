package de.dogeapp.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object Http {
    /** Simple GET returning the body as text; throws on network errors or HTTP status >= 400. */
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "DogeControl/1.0 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code >= 400) throw java.io.IOException("HTTP $code")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
