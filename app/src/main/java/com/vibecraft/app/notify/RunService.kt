package com.vibecraft.app.notify

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat

/**
 * Keeps the app alive while the AI works, so a task goes on after you leave the app or switch the screen off.
 * It holds no logic of its own: the work runs in the app, the service only makes Android leave it alone,
 * and the wake lock keeps the processor awake until the task ends (with a safety limit).
 */
class RunService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ok = runCatching {
            ServiceCompat.startForeground(this, Notifier.ID_PROGRESS, Notifier.foregroundNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        if (wake == null) {
            wake = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vibecraft:run")?.apply {
                setReferenceCounted(false)
                acquire(20 * 60 * 1000L)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wake?.takeIf { it.isHeld }?.release()
        wake = null
        super.onDestroy()
    }
}
