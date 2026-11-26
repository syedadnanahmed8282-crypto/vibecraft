package com.vibecraft.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vibecraft.app.MainActivity
import com.vibecraft.app.R
import com.vibecraft.app.gen.Notice
import com.vibecraft.app.gen.NoticeKind
import com.vibecraft.app.gen.ProgressLine

/**
 * Everything the phone shows or plays while the app is not in front: the ongoing "working" notification,
 * and the three kinds of result notifications, each with its own channel, color and sound.
 */
object Notifier {
    const val CH_PROGRESS = "progress"
    const val CH_DONE = "done"
    const val CH_ASK = "ask"
    const val CH_PROBLEM = "problem"
    const val ID_PROGRESS = 7001
    private val RESULT_IDS = setOf(7011, 7012, 7013)

    /** True while any screen of the app is visible. Set by the application's lifecycle callbacks. */
    @Volatile var foreground = false

    private val main = Handler(Looper.getMainLooper())
    private val green = 0xFF1F6B43.toInt()
    private var startedAt = 0L
    private var running = false
    private var generation = 0
    @Volatile private var lastLine = ProgressLine("Vibecraft", "Working", null)

    fun init(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun sound(res: Int): Uri = Uri.parse("android.resource://${ctx.packageName}/$res")
        val progress = NotificationChannel(CH_PROGRESS, "Running tasks", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while Vibecraft is working in the background"
            setShowBadge(false)
        }
        val done = NotificationChannel(CH_DONE, "Finished and replies", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A task is finished, or Vibecraft replied while you were away"
            setSound(sound(R.raw.vc_done), attrs)
            enableVibration(true); vibrationPattern = longArrayOf(0, 40, 70, 40, 70, 90)
        }
        val ask = NotificationChannel(CH_ASK, "Needs your answer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Vibecraft needs a decision or an answer before it continues"
            setSound(sound(R.raw.vc_ask), attrs)
            enableVibration(true); vibrationPattern = longArrayOf(0, 60, 110, 90)
        }
        val problem = NotificationChannel(CH_PROBLEM, "Problems", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A task stopped or something went wrong"
            setSound(sound(R.raw.vc_problem), attrs)
            enableVibration(true); vibrationPattern = longArrayOf(0, 120, 120)
        }
        nm.createNotificationChannels(listOf(progress, done, ask, problem))
    }

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun openSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    fun idOf(kind: NoticeKind): Int = when (kind) {
        NoticeKind.DONE, NoticeKind.REPLY -> 7011
        NoticeKind.ASK -> 7012
        NoticeKind.PROBLEM -> 7013
    }

    private fun openIntent(ctx: Context, projectId: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (projectId != null) i.putExtra("open", projectId)
        return PendingIntent.getActivity(ctx, (projectId ?: "app").hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * A result notification. A new one of the same kind for the same chat replaces the old one.
     * silent = true keeps it quiet (the user turned sounds off); okLabel adds a one-tap answer button.
     */
    @SuppressLint("MissingPermission")
    fun post(ctx: Context, projectId: String?, n: Notice, silent: Boolean, okLabel: String? = null, okText: String? = null) {
        if (!canPost(ctx)) return
        val channel = when (n.kind) {
            NoticeKind.DONE, NoticeKind.REPLY -> CH_DONE
            NoticeKind.ASK -> CH_ASK
            NoticeKind.PROBLEM -> CH_PROBLEM
        }
        val color = when (n.kind) {
            NoticeKind.DONE, NoticeKind.REPLY -> 0xFF22A45D.toInt()
            NoticeKind.ASK -> 0xFFF59E0B.toInt()
            NoticeKind.PROBLEM -> 0xFFE0564A.toInt()
        }
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(color)
            .setContentTitle(n.title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.big))
            .setContentIntent(openIntent(ctx, projectId))
            .setAutoCancel(true)
            .setCategory(if (n.kind == NoticeKind.PROBLEM) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(if (n.kind == NoticeKind.DONE || n.kind == NoticeKind.REPLY) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setSilent(silent)
        if (okLabel != null && okText != null && projectId != null && n.kind == NoticeKind.ASK) {
            val i = Intent(ctx, NotifyActionReceiver::class.java).putExtra("project", projectId).putExtra("text", okText)
            val pi = PendingIntent.getBroadcast(ctx, ("ok" + projectId).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(0, okLabel, pi)
        }
        NotificationManagerCompat.from(ctx).notify(projectId ?: "app", idOf(n.kind), b.build())
    }

    fun clearResults(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.activeNotifications.filter { it.id in RESULT_IDS }.forEach { nm.cancel(it.tag, it.id) } }
    }

    // ---- the ongoing "working" notification, held by RunService ----

    private fun progressNotification(ctx: Context, line: ProgressLine): Notification =
        NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(green)
            .setContentTitle(line.title)
            .setContentText(line.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setWhen(startedAt).setShowWhen(true).setUsesChronometer(true)
            .setProgress(100, line.percent ?: 0, line.percent == null)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent(ctx, null))
            .build()

    fun foregroundNotification(ctx: Context): Notification = progressNotification(ctx, lastLine)

    fun runStarted(ctx: Context, line: ProgressLine) {
        val app = ctx.applicationContext
        generation++
        startedAt = System.currentTimeMillis()
        running = true
        lastLine = line
        // Starting a foreground service can be refused (for example from the background); the work then just continues.
        runCatching { ContextCompat.startForegroundService(app, Intent(app, RunService::class.java)) }
    }

    @SuppressLint("MissingPermission")
    fun runProgress(ctx: Context, line: ProgressLine) {
        lastLine = line
        if (!running || !canPost(ctx)) return
        NotificationManagerCompat.from(ctx).notify(ID_PROGRESS, progressNotification(ctx, line))
    }

    /** Stops the service. A service stopped within a moment of starting would crash, so it is given a short minimum life. */
    fun runEnded(ctx: Context) {
        val app = ctx.applicationContext
        running = false
        val gen = generation
        val stop = Runnable { if (gen == generation && !running) app.stopService(Intent(app, RunService::class.java)) }
        val age = System.currentTimeMillis() - startedAt
        if (age < 1500) main.postDelayed(stop, 1500 - age) else main.post(stop)
    }
}
