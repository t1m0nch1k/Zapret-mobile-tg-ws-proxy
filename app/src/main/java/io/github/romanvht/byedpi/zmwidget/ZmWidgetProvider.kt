package io.github.romanvht.byedpi.zmwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.MainActivity
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.START_ACTION
import io.github.romanvht.byedpi.data.STOP_ACTION
import io.github.romanvht.byedpi.receiver.ServiceActionReceiver
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode

/** Виджет рабочего стола: статус и включение/выключение обхода. */
class ZmWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ServiceManager.refresh(context)
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> toggle(context)
            ACTION_SETTINGS -> openSettings(context)
        }
        super.onReceive(context, intent)
    }

    private fun toggle(context: Context) {
        ServiceManager.refresh(context)
        val prefs = context.getPreferences()
        val mode = prefs.mode()
        val (status, _) = appStatus
        if (status == AppStatus.Running) {
            context.sendBroadcast(
                Intent(context, ServiceActionReceiver::class.java)
                    .setAction(STOP_ACTION)
                    .putExtra(ServiceActionReceiver.EXTRA_MODE, mode.name)
            )
        } else {
            if (mode == Mode.VPN && VpnService.prepare(context) != null) {
                // Нужно согласие пользователя на VPN — открываем приложение.
                openSettings(context)
                return
            }
            context.sendBroadcast(
                Intent(context, ServiceActionReceiver::class.java)
                    .setAction(START_ACTION)
                    .putExtra(ServiceActionReceiver.EXTRA_MODE, mode.name)
            )
        }
        Handler(Looper.getMainLooper()).postDelayed({ updateAll(context) }, 900)
    }

    private fun openSettings(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.romanvht.byedpi.zmwidget.TOGGLE"
        const val ACTION_SETTINGS = "io.github.romanvht.byedpi.zmwidget.SETTINGS"

        /** Системный запрос закрепления виджета (Android 8+). */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, ZmWidgetProvider::class.java), null, null)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ZmWidgetProvider::class.java))
            if (ids == null || ids.isEmpty()) return
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val (status, mode) = appStatus
            val running = status == AppStatus.Running
            val views = RemoteViews(context.packageName, R.layout.widget_zm)
            views.setTextViewText(
                R.id.widget_status,
                context.getString(if (running) R.string.zm_widget_on else R.string.zm_widget_off)
            )
            views.setTextViewText(
                R.id.widget_mode,
                if (running) {
                    context.getString(
                        R.string.zm_widget_mode,
                        context.getString(if (mode == Mode.VPN) R.string.zm_widget_mode_vpn else R.string.zm_widget_mode_proxy)
                    )
                } else ""
            )
            views.setTextViewText(
                R.id.widget_toggle,
                context.getString(if (running) R.string.zm_widget_disable else R.string.zm_widget_enable)
            )
            views.setOnClickPendingIntent(R.id.widget_toggle, broadcast(context, ACTION_TOGGLE, 1))
            views.setOnClickPendingIntent(R.id.widget_settings, broadcast(context, ACTION_SETTINGS, 2))
            manager.updateAppWidget(id, views)
        }

        private fun broadcast(context: Context, action: String, request: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                request,
                Intent(context, ZmWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}