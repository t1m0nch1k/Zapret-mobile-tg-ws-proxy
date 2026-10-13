package io.github.romanvht.byedpi.zmupdate

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.romanvht.byedpi.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * Проверка обновлений в GitHub Releases.
 *
 * Сначала запрос идёт напрямую, при неудаче — через локальный SOCKS5 ByeDPI
 * (127.0.0.1:1080), потому что сеть провайдера часто режет GitHub.
 */
object UpdateChecker {
    const val REPO_OWNER = "t1m0nch1k"
    const val REPO_NAME = "Zapret-mobile-tg-ws-proxy"
    private const val API_LATEST =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    private const val PREF_LAST_CHECK = "zm_update_last_check"
    private const val PREF_SKIPPED_TAG = "zm_update_skipped_tag"
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    data class Release(
        val tag: String,
        val version: String,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String?,
    )

    fun currentVersion(): String = BuildConfig.VERSION_NAME.substringBefore('-')

    fun shouldAutoCheck(prefs: SharedPreferences): Boolean =
        System.currentTimeMillis() - prefs.getLong(PREF_LAST_CHECK, 0L) > CHECK_INTERVAL_MS

    fun markChecked(prefs: SharedPreferences) =
        prefs.edit(commit = true) { putLong(PREF_LAST_CHECK, System.currentTimeMillis()) }

    fun skippedTag(prefs: SharedPreferences): String =
        prefs.getString(PREF_SKIPPED_TAG, "") ?: ""

    fun skip(prefs: SharedPreferences, tag: String) =
        prefs.edit(commit = true) { putString(PREF_SKIPPED_TAG, tag) }

    /** Ответ сервера с кодом, отличным от 2xx. */
    class HttpStatusException(val code: Int) : IOException("HTTP $code")

    /** Синхронный запрос к GitHub; бросает IOException при неудаче. */
    fun fetchLatest(): Release {
        val body = try {
            httpGet(API_LATEST)
        } catch (e: HttpStatusException) {
            if (e.code == 404) throw IOException("в репозитории пока нет релизов") else throw e
        }
        val json = JSONObject(body)
        val tag = json.optString("tag_name").ifBlank { throw IOException("Пустой tag_name") }
        val notes = json.optString("body", "")
        val page = json.optString(
            "html_url", "https://github.com/$REPO_OWNER/$REPO_NAME/releases"
        )
        var apkUrl: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url", "")
                if (url.isBlank()) continue
                val arm64 = name.contains("arm64", ignoreCase = true)
                if (apkUrl == null || arm64) apkUrl = url
                if (arm64) break
            }
        }
        return Release(
            tag = tag, version = tag.removePrefix("v"), notes = notes,
            pageUrl = page, apkUrl = apkUrl,
        )
    }

    fun isNewer(remote: String, local: String = currentVersion()): Boolean {
        val a = numbers(remote)
        val b = numbers(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(v: String): List<Int> =
        v.trim().removePrefix("v").split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /** Скачать APK (с тем же резервом через локальный SOCKS5). */
    fun downloadApk(url: String, target: File) {
        target.parentFile?.mkdirs()
        val connection = openWithFallback(url)
        connection.inputStream.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun httpGet(url: String): String {
        val connection = openWithFallback(url)
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun openWithFallback(url: String): HttpURLConnection {
        var last: Throwable? = null
        for (proxy in listOf<Proxy?>(null, localSocksProxy())) {
            try {
                return open(url, proxy)
            } catch (e: HttpStatusException) {
                // Сеть работает, сервер ответил — резервный маршрут не нужен.
                throw e
            } catch (t: Throwable) {
                last = t
            }
        }
        throw IOException(last?.message ?: "Нет соединения")
    }

    private fun localSocksProxy(): Proxy? = runCatching {
        Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))
    }.getOrNull()

    private fun open(url: String, proxy: Proxy?): HttpURLConnection {
        val connection = URL(url).openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "ZapretMobile/" + currentVersion())
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw HttpStatusException(code)
        }
        return connection
    }
}