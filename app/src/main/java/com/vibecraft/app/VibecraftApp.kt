package com.vibecraft.app

import android.app.Application
import androidx.room.Room
import com.vibecraft.app.data.AppDb
import com.vibecraft.app.data.MIGRATION_1_2
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

class VibecraftApp : Application() {
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
    }
}
