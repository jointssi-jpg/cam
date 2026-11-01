package com.sami.joocam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sami.joocam.ConnState
import com.sami.joocam.MainViewModel
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun LiveScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    var videoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    val fs = vm.fullscreen

    Column(modifier.fillMaxSize().background(Ink)) {
        val boxMod = if (fs) Modifier.weight(1f).fillMaxWidth()
        else Modifier.fillMaxWidth().aspectRatio(16f / 9f)

        Box(boxMod.background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VLCVideoLayout(ctx).also {
                        videoLayout = it
                        vm.player.attach(it)
                    }
                },
                onRelease = { vm.player.detach() },
            )
            StatusPill(vm, Modifier.align(Alignment.TopStart).padding(10.dp))
            if (vm.conn != ConnState.PLAYING) CenterNotice(vm)
            if (fs) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color(0x99000000)).padding(6.dp),
                ) { Controls(vm, videoLayout) }
            }
        }

        if (!fs) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Controls(vm, videoLayout)
                Text(
                    "${vm.config.host}  |  ${vm.networkLabel}  |  " +
                        if (vm.useSub) "Substream" else "Main stream",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (vm.message.isNotBlank() && vm.conn != ConnState.PLAYING) {
                    Text(vm.message, color = Amber, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(vm: MainViewModel, modifier: Modifier) {
    val (label, color) = when (vm.conn) {
        ConnState.PLAYING -> "Live" to LiveRed
        ConnState.CONNECTING -> "Connecting" to Amber
        ConnState.RECONNECTING -> "Reconnecting" to Amber
        ConnState.ERROR -> "Error" to LiveRed
        ConnState.IDLE -> "Stopped" to Steel
    }
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xAA000000))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BoxScope.CenterNotice(vm: MainViewModel) {
    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (vm.conn) {
            ConnState.CONNECTING, ConnState.RECONNECTING ->
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            ConnState.ERROR -> Text(vm.message, color = Amber)
            ConnState.IDLE -> Text("Tap play to watch the camera", color = Steel)
            else -> Unit
        }
    }
}

@Composable
private fun Controls(vm: MainViewModel, layout: VLCVideoLayout?) {
    val playing = vm.conn != ConnState.IDLE && vm.conn != ConnState.ERROR
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = { if (playing) vm.stop() else vm.play() }) {
            Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (playing) "Stop" else "Play")
        }
        IconButton(onClick = { vm.toggleMute() }) {
            Icon(if (vm.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, if (vm.muted) "Unmute" else "Mute")
        }
        IconButton(onClick = { vm.saveSnapshot(vm.player.captureFrame(layout)) }) {
            Icon(Icons.Filled.PhotoCamera, "Snapshot")
        }
        FilterChip(
            selected = vm.useSub,
            onClick = { vm.selectQuality(!vm.useSub) },
            label = { Text(if (vm.useSub) "Sub" else "Main") },
        )
        IconButton(onClick = { vm.fullscreen = !vm.fullscreen }) {
            Icon(
                if (vm.fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                if (vm.fullscreen) "Exit full screen" else "Full screen",
            )
        }
    }
}
