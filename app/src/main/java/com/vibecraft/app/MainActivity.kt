package com.vibecraft.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.vibecraft.app.ui.AppRoot
import com.vibecraft.app.ui.VibecraftTheme

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VibecraftTheme(vm.theme) { AppRoot(vm) } }
    }
}
