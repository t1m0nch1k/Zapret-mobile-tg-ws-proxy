package io.github.romanvht.byedpi.zmupdate

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.MainActivity
import io.github.romanvht.byedpi.utility.getPreferences
import java.io.File
import java.util.concurrent.Executors

/** Диалоги, уведомление и установка обновления из GitHub Releases. */
object UpdateUi {
    const val EXTRA_SHOW_UPDATE = "zm_show_update"
    private const val CHANNEL_ID = "zm_updates"
    private const val NOTIFICATION_ID = 103

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Ручная проверка из настроек — всегда показывает результат. */
    fun checkManually(context: Context) {
        val prefs = context.getPreferences()
        Toast.makeText(context, R.string.zm_update_checking, Toast.LENGTH_SHORT).show()
        executor.execute {
            val result = runCatching { UpdateChecker.fetchLatest() }
            result.onSuccess { UpdateChecker.markChecked(prefs) }
            main.post {
                result.onSuccess { release ->
                    if (UpdateChecker.isNewer(release.version)) {
                        showDialog(context, release, prefs)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.zm_update_latest, UpdateChecker.currentVersion()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.zm_update_error, error.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** Тихая проверка (не чаще раза в сутки); при новой версии — уведомление. */
    fun maybeAutoCheck(context: Context) {
        val prefs = context.getPreferences()
        if (!UpdateChecker.shouldAutoCheck(prefs)) return
        executor.execute {
            val result = runCatching { UpdateChecker.fetchLatest() }
            UpdateChecker.markChecked(prefs)
            val release = result.getOrNull() ?: return@execute
            if (!UpdateChecker.isNewer(release.version)) return@execute
            if (UpdateChecker.skippedTag(prefs) == release.tag) return@execute
            notifyUpdate(context.applicationContext, release)
        }
    }

    /** Открыть диалог обновления (например, по тапу на уведомление). */
    fun showLatestDialog(context: Context) {
        val prefs = context.getPreferences()
        executor.execute {
            val result = runCatching { UpdateChecker.fetchLatest() }
            main.post {
                result.onSuccess { release ->
                    if (UpdateChecker.isNewer(release.version)) {
                        showDialog(context, release, prefs)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.zm_update_latest, UpdateChecker.currentVersion()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.zm_update_error, error.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun notifyUpdate(context: Context, release: UpdateChecker.Release) {
        main.post {
            runCatching {
                createChannel(context)
                val intent = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_SHOW_UPDATE, true)
                val pending = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(
                        context.getString(R.string.zm_update_available_title, release.version)
                    )
                    .setContentText(context.getString(R.string.zm_update_available_text))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.zm_update_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun showDialog(context: Context, release: UpdateChecker.Release, prefs: SharedPreferences) {
        val activity = context as? Activity ?: return
        val message = buildString {
            append(
                activity.getString(
                    R.string.zm_update_dialog_header,
                    UpdateChecker.currentVersion(),
                    release.version
                )
            )
            if (release.notes.isNotBlank()) {
                append("\n\n")
                append(release.notes.take(1200))
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.zm_update_title)
            .setMessage(message)
            .setPositiveButton(R.string.zm_update_download) { _, _ -> downloadAndInstall(activity, release) }
            .setNeutralButton(R.string.zm_update_open_page) { _, _ -> openUrl(activity, release.pageUrl) }
            .setNegativeButton(R.string.zm_update_later) { _, _ -> UpdateChecker.skip(prefs, release.tag) }
            .show()
    }

    private fun downloadAndInstall(context: Context, release: UpdateChecker.Release) {
        val url = release.apkUrl
        if (url.isNullOrBlank()) {
            openUrl(context, release.pageUrl)
            return
        }
        Toast.makeText(context, R.string.zm_update_downloading, Toast.LENGTH_LONG).show()
        executor.execute {
            val result = runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "ZapretMobile-" + release.version + ".apk")
                if (target.exists()) target.delete()
                UpdateChecker.downloadApk(url, target)
                target
            }
            main.post {
                result.onSuccess { file -> installApk(context, file) }
                    .onFailure { openUrl(context, release.pageUrl) }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            Toast.makeText(context, R.string.zm_update_error_install, Toast.LENGTH_LONG).show()
        }
    }

    private fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, url, Toast.LENGTH_LONG).show()
        }
    }
}