package com.sami.joocam

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sami.joocam.data.AuthMethod
import com.sami.joocam.data.CameraConfig
import com.sami.joocam.data.SettingsStore
import com.sami.joocam.data.stripCredentials
import com.sami.joocam.onvif.OnvifClient
import com.sami.joocam.player.PlayerEvent
import com.sami.joocam.player.RtspPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnState { IDLE, CONNECTING, PLAYING, RECONNECTING, ERROR }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val onvif = OnvifClient()
    val player = RtspPlayer(app)

    var config by mutableStateOf(store.load()); private set
    var conn by mutableStateOf(ConnState.IDLE); private set
    var message by mutableStateOf(""); private set
    var muted by mutableStateOf(false); private set
    var useSub by mutableStateOf(false); private set
    var fullscreen by mutableStateOf(false)
    var networkLabel by mutableStateOf("Checking network"); private set
    var onvifLog by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set

    /** One-off messages for the UI (snackbar). */
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var wantPlaying = false
    private var inBackground = false
    private var networkUp = true
    private var retry = 0
    private var reconnectJob: Job? = null
    private var watchdog: Job? = null

    init {
        player.listener = { ev ->
            when (ev) {
                PlayerEvent.Playing -> {
                    watchdog?.cancel()
                    retry = 0
                    conn = ConnState.PLAYING
                    message = ""
                }
                PlayerEvent.Error, PlayerEvent.Ended -> if (wantPlaying) scheduleReconnect("Stream lost")
                is PlayerEvent.Buffering -> Unit
            }
        }
        watchNetwork()
    }

    // ---- playback --------------------------------------------------------------------------

    fun play() {
        wantPlaying = true
        retry = 0
        startPlayback()
    }

    fun stop() {
        wantPlaying = false
        reconnectJob?.cancel()
        watchdog?.cancel()
        player.stop()
        conn = ConnState.IDLE
        message = ""
    }

    fun toggleMute() {
        muted = !muted
        player.setMuted(muted)
    }

    fun selectQuality(sub: Boolean) {
        if (sub && config.subUri.isBlank()) {
            toasts.tryEmit("No substream yet. Open Settings and fetch it via ONVIF.")
            return
        }
        useSub = sub
        if (wantPlaying) play()
    }

    private fun startPlayback() {
        reconnectJob?.cancel()
        watchdog?.cancel()
        val cfg = config
        if (cfg.auth != AuthMethod.NONE && cfg.password.isBlank()) {
            wantPlaying = false
            conn = ConnState.ERROR
            message = "Enter the camera password in Settings first."
            return
        }
        val uri = if (useSub && cfg.subUri.isNotBlank()) cfg.subUri else cfg.effectiveMain
        conn = if (retry > 0) ConnState.RECONNECTING else ConnState.CONNECTING
        player.play(
            uri = uri,
            user = if (cfg.auth == AuthMethod.NONE) null else cfg.username,
            password = if (cfg.auth == AuthMethod.NONE) null else cfg.password,
            muted = muted,
        )
        // VLC can sit in "opening" forever when a host is unreachable.
        watchdog = viewModelScope.launch {
            delay(15_000)
            if (wantPlaying && conn != ConnState.PLAYING) scheduleReconnect("No response from camera")
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (inBackground) return
        watchdog?.cancel()
        reconnectJob?.cancel()
        conn = ConnState.RECONNECTING
        message = reason
        reconnectJob = viewModelScope.launch {
            val wait = minOf(2_000L shl minOf(retry, 3), 15_000L)
            retry++
            delay(wait)
            if (!wantPlaying) return@launch
            if (!networkUp) {
                message = "Waiting for network"
                return@launch // network callback restarts playback
            }
            startPlayback()
        }
    }

    fun onBackground() {
        inBackground = true
        reconnectJob?.cancel()
        watchdog?.cancel()
        if (wantPlaying) {
            player.stop()
            conn = ConnState.IDLE
        }
    }

    fun onForeground() {
        inBackground = false
        if (wantPlaying) {
            retry = 0
            startPlayback()
        }
    }

    private fun watchNetwork() {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        fun label(caps: NetworkCapabilities?): String = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
        networkLabel = label(cm.getNetworkCapabilities(cm.activeNetwork))
        networkUp = cm.activeNetwork != null
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkUp = true
                viewModelScope.launch {
                    networkLabel = label(cm.getNetworkCapabilities(network))
                    if (wantPlaying && !inBackground && conn != ConnState.PLAYING) {
                        retry = 0
                        startPlayback()
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                networkLabel = label(caps)
            }

            override fun onLost(network: Network) {
                networkUp = false
                networkLabel = "Offline"
            }
        })
    }

    // ---- settings / ONVIF ------------------------------------------------------------------

    fun saveConfig(c: CameraConfig) {
        config = c.copy(mainUri = stripCredentials(c.mainUri), subUri = stripCredentials(c.subUri))
        store.save(config)
        toasts.tryEmit("Settings saved")
    }

    fun fetchOnvifStreams(c: CameraConfig) {
        saveConfig(c)
        if (config.password.isBlank()) {
            onvifLog = "Enter the camera password first."
            return
        }
        viewModelScope.launch {
            busy = true
            onvifLog = "Contacting ${config.host}:${config.onvifPort}..."
            try {
                val profiles = onvif.getProfiles(config)
                if (profiles.isEmpty()) {
                    onvifLog = "Camera returned no media profiles."
                    return@launch
                }
                val lines = StringBuilder("Profiles found:\n")
                profiles.forEach { lines.append("- ${it.token} (${it.name}) ${it.resolution}\n") }
                val main = profiles.maxByOrNull { it.area }!!
                val sub = profiles.firstOrNull { it.token == "profile_1" }
                    ?: profiles.filter { it != main }.minByOrNull { it.area }
                val mainUri = onvif.getStreamUri(config, main.token)
                lines.append("\nMain (${main.token}): $mainUri\n")
                if (sub != null) {
                    val subUri = onvif.getStreamUri(config, sub.token)
                    lines.append("Sub (${sub.token}): $subUri\n")
                    config = config.copy(subUri = subUri)
                    store.save(config)
                    lines.append("\nSubstream saved. Pick \"Sub\" on the Live tab.")
                } else {
                    lines.append("\nNo separate substream profile found.")
                }
                onvifLog = lines.toString()
            } catch (e: Exception) {
                onvifLog = "ONVIF failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    // ---- snapshot --------------------------------------------------------------------------

    fun saveSnapshot(bitmap: Bitmap?) {
        if (bitmap == null) {
            toasts.tryEmit("No video frame to capture yet")
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val name = "JooCam_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JooCam")
                    }
                    val resolver = getApplication<Application>().contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                    resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                }.isSuccess
            }
            toasts.tryEmit(if (ok) "Snapshot saved to Pictures/JooCam" else "Could not save snapshot")
        }
    }

    override fun onCleared() {
        player.listener = null
        player.release()
    }
}
