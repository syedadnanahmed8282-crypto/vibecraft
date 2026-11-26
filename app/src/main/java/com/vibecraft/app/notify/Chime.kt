package com.vibecraft.app.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.vibecraft.app.R
import com.vibecraft.app.gen.NoticeKind

/** Plays the notification sounds while the app is open, when a system notification would only be noise. */
object Chime {
    private var pool: SoundPool? = null
    private val ids = HashMap<NoticeKind, Int>()
    private val ready = HashSet<Int>()

    fun init(ctx: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { _, sid, status -> if (status == 0) synchronized(ready) { ready.add(sid) } }
        val done = p.load(ctx, R.raw.vc_done, 1)
        ids[NoticeKind.DONE] = done
        ids[NoticeKind.REPLY] = done
        ids[NoticeKind.ASK] = p.load(ctx, R.raw.vc_ask, 1)
        ids[NoticeKind.PROBLEM] = p.load(ctx, R.raw.vc_problem, 1)
        pool = p
    }

    fun play(kind: NoticeKind, volume: Float = 0.85f) {
        val p = pool ?: return
        val id = ids[kind] ?: return
        if (synchronized(ready) { id in ready }) p.play(id, volume, volume, 1, 0, 1f)
    }
}
