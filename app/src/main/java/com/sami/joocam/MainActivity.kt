package com.sami.joocam

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sami.joocam.ui.JooCamTheme
import com.sami.joocam.ui.LiveScreen
import com.sami.joocam.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = vm.onForeground()
            override fun onStop(owner: LifecycleOwner) = vm.onBackground()
        })
        setContent { JooCamTheme { App(vm) } }
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val activity = LocalContext.current as Activity
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.toasts.collect { snackbar.showSnackbar(it) } }

    BackHandler(enabled = vm.fullscreen) { vm.fullscreen = false }

    LaunchedEffect(vm.fullscreen) {
        activity.requestedOrientation = if (vm.fullscreen)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val c = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (vm.fullscreen) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!vm.fullscreen) NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Videocam, null) }, label = { Text("Live") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
            }
        },
    ) { pad ->
        if (tab == 0) LiveScreen(vm, Modifier.padding(pad)) else SettingsScreen(vm, Modifier.padding(pad))
    }
}
