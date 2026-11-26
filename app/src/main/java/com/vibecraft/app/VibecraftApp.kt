package com.vibecraft.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import com.vibecraft.app.data.AppDb
import com.vibecraft.app.data.MIGRATION_1_2
import com.vibecraft.app.notify.Chime
import com.vibecraft.app.notify.Notifier
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * The app's ViewModel lives as long as the process, not as long as one screen: a task keeps running, and keeps
 * reporting its progress, after the activity is closed or swiped away. Notifications reach the same instance.
 */
class VibecraftApp : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    lateinit var db: AppDb
        private set

    val supabase by lazy {
        createSupabaseClient(Config.SUPABASE_URL, Config.SUPABASE_KEY) {
            install(Auth)
            install(Postgrest)
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDb::class.java, "vibecraft.db").addMigrations(MIGRATION_1_2).build()
        Notifier.init(this)
        Chime.init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++
                Notifier.foreground = true
                if (started == 1) Notifier.clearResults(this@VibecraftApp)   // the user is back: what the notifications said is in the chat
            }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) Notifier.foreground = false
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

/** The one AppVm of the process. */
fun Application.appVm(): AppVm =
    ViewModelProvider(this as ViewModelStoreOwner, ViewModelProvider.AndroidViewModelFactory.getInstance(this))[AppVm::class.java]
