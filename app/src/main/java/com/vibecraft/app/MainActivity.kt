package com.vibecraft.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vibecraft.app.ui.AppRoot
import com.vibecraft.app.ui.VibecraftTheme

class MainActivity : ComponentActivity() {
    private val vm: AppVm by lazy { application.appVm() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openFrom(intent)
        setContent { VibecraftTheme(vm.theme) { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshNotifyState()
    }

    /** A tapped notification carries the chat it is about. */
    private fun openFrom(i: Intent?) {
        i?.getStringExtra("open")?.let { vm.openRequest = it }
    }
}
