package io.github.romanvht.byedpi.zmsocks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.MainActivity

/**
 * Foreground service that keeps the SOCKS5 WebSocket bridge alive.
 *
 * The Python side (zm_runner over the vendored tg-ws-proxy core) is started on a
 * background thread: Python.start() unpacks the runtime on first use and may take
 * a moment, so startForeground() is called first to satisfy the 5 second rule.
 */
class ZmSocksBridgeService : Service() {

    private var port = ZmSocksBridge.DEFAULT_PORT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBridge()
            return START_NOT_STICKY
        }

        port = intent?.getIntExtra(EXTRA_PORT, -1)?.takeIf { it > 0 }
            ?: ZmSocksBridge.port(preferences())
        startBridge()
        return START_STICKY
    }

    private fun startBridge() {
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.zm_bridge_notification, port))
        )

        val prefs = preferences()
        Thread {
            val result = try {
                if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                Python.getInstance()
                    .getModule("zm_runner")
                    .callAttr("start", port)
                    .toString()
            } catch (t: Throwable) {
                Log.e(TAG, "bridge start failed", t)
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
            Log.i(TAG, "bridge start: $result")
            ZmSocksBridge.writeStatus(prefs, result)
        }.start()
    }

    private fun stopBridge() {
        val prefs = preferences()
        Thread {
            val result = try {
                if (Python.isStarted()) {
                    Python.getInstance().getModule("zm_runner").callAttr("stop").toString()
                } else {
                    "not running"
                }
            } catch (t: Throwable) {
                Log.e(TAG, "bridge stop failed", t)
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
            Log.i(TAG, "bridge stop: $result")
            ZmSocksBridge.writeStatus(prefs, "")
        }.start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun preferences(): SharedPreferences =
        getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.zm_bridge_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.description = getString(R.string.zm_bridge_channel_desc)
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.zm_bridge_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "ZmSocksBridge"
        private const val CHANNEL_ID = "ZM_Socks_Bridge_v1"
        private const val NOTIFICATION_ID = 102

        const val ACTION_START = "io.github.romanvht.byedpi.zmsocks.action.START"
        const val ACTION_STOP = "io.github.romanvht.byedpi.zmsocks.action.STOP"
        const val EXTRA_PORT = "extra_port"
    }
}