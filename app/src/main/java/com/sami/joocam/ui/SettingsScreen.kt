package com.sami.joocam.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sami.joocam.MainViewModel
import com.sami.joocam.data.AuthMethod
import com.sami.joocam.data.CameraConfig

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = vm.config
    var host by remember { mutableStateOf(c.host) }
    var rtspPort by remember { mutableStateOf(c.rtspPort.toString()) }
    var onvifPort by remember { mutableStateOf(c.onvifPort.toString()) }
    var user by remember { mutableStateOf(c.username) }
    var pass by remember { mutableStateOf(c.password) }
    var mainUri by remember { mutableStateOf(c.mainUri) }
    var subUri by remember { mutableStateOf(c.subUri) }
    var auth by remember { mutableStateOf(c.auth) }

    // Pick up a substream URI that the ONVIF fetch stored.
    LaunchedEffect(vm.config.subUri) { subUri = vm.config.subUri }

    fun current() = CameraConfig(
        host = host.trim(),
        rtspPort = rtspPort.toIntOrNull() ?: 554,
        onvifPort = onvifPort.toIntOrNull() ?: 8899,
        username = user.trim(),
        password = pass,
        mainUri = mainUri,
        subUri = subUri,
        auth = auth,
    )

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Camera", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(host, { host = it }, label = { Text("IP address or hostname") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(rtspPort, { rtspPort = it.filter(Char::isDigit) }, label = { Text("RTSP port") },
                singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(onvifPort, { onvifPort = it.filter(Char::isDigit) }, label = { Text("ONVIF port") },
                singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        OutlinedTextField(user, { user = it }, label = { Text("Username") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Password") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
            supportingText = { Text("Stored encrypted with the Android Keystore.") })

        Text("RTSP authentication", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthMethod.entries.forEach { m ->
                FilterChip(selected = auth == m, onClick = { auth = m },
                    label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }

        OutlinedTextField(mainUri, { mainUri = it }, label = { Text("Main stream URI (optional)") },
            placeholder = { Text("rtsp://${host.ifBlank { "host" }}:$rtspPort/live/ch00_0") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(subUri, { subUri = it }, label = { Text("Substream URI (optional)") },
            modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.saveConfig(current()) }) { Text("Save") }
            OutlinedButton(onClick = { vm.fetchOnvifStreams(current()) }, enabled = !vm.busy) {
                Text(if (vm.busy) "Working" else "Fetch streams via ONVIF")
            }
        }

        if (vm.onvifLog.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Text(vm.onvifLog, Modifier.padding(12.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
