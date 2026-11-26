package com.vibecraft.app.notify

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.vibecraft.app.appVm
import com.vibecraft.app.gen.NoticeKind

/** Sends the answer written on a notification button into the chat, so the work can go on without opening the app. */
class NotifyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val project = intent.getStringExtra("project") ?: return
        val text = intent.getStringExtra("text") ?: return
        val app = context.applicationContext as Application
        NotificationManagerCompat.from(app).cancel(project, Notifier.idOf(NoticeKind.ASK))
        app.appVm().send(project, text)
    }
}
