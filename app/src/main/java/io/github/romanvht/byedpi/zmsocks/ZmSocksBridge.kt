package io.github.romanvht.byedpi.zmsocks

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * SOCKS5 -> WebSocket bridge for Telegram.
 *
 * Runs the vendored tg-ws-proxy core (MIT, https://github.com/Flowseal/tg-ws-proxy)
 * on Chaquopy, so the phone talks to Telegram exactly like the desktop build does:
 * Telegram -> SOCKS5 (127.0.0.1) -> WSS -> DC.
 *
 * Mirrors [io.github.romanvht.byedpi.ewenloy.tgws.EwenloyTgWsServiceExtension]:
 * start/stop through Intents, state in the shared preferences.
 */
object ZmSocksBridge {
    const val PREF_ENABLED = "zm_socks_bridge_enabled"
    const val PREF_PORT = "zm_socks_bridge_port"
    const val PREF_STATUS = "zm_socks_bridge_status"

    const val DEFAULT_PORT = 1081
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535

    fun isEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_ENABLED, false)

    fun port(prefs: SharedPreferences): Int =
        prefs.getString(PREF_PORT, DEFAULT_PORT.toString())
            ?.toIntOrNull()
            ?.takeIf { it in MIN_PORT..MAX_PORT }
            ?: DEFAULT_PORT

    fun refresh(context: Context, prefs: SharedPreferences) {
        if (isEnabled(prefs)) start(context, prefs) else stop(context)
    }

    fun start(context: Context, prefs: SharedPreferences) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ZmSocksBridgeService::class.java).apply {
                action = ZmSocksBridgeService.ACTION_START
                putExtra(ZmSocksBridgeService.EXTRA_PORT, port(prefs))
            }
        )
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, ZmSocksBridgeService::class.java).apply {
                action = ZmSocksBridgeService.ACTION_STOP
            }
        )
    }

    fun readStatus(prefs: SharedPreferences): String =
        prefs.getString(PREF_STATUS, "") ?: ""

    fun writeStatus(prefs: SharedPreferences, value: String) {
        prefs.edit(commit = true) { putString(PREF_STATUS, value) }
    }
}