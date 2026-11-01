package com.sami.joocam.data

enum class AuthMethod { NONE, BASIC, DIGEST }

data class CameraConfig(
    val host: String = "192.168.0.104",
    val rtspPort: Int = 554,
    val onvifPort: Int = 8899,
    val username: String = "admin",
    val password: String = "",
    val mainUri: String = "",
    val subUri: String = "",
    val auth: AuthMethod = AuthMethod.BASIC,
) {
    /** Main stream URI; falls back to the verified path for this camera. Never contains credentials. */
    val effectiveMain: String
        get() = mainUri.ifBlank { "rtsp://$host:$rtspPort/live/ch00_0" }

    val onvifDeviceUrl: String get() = "http://$host:$onvifPort/onvif/device_service"
    val onvifMediaUrl: String get() = "http://$host:$onvifPort/onvif/Media"
}

private val CREDS = Regex("^(rtsp://)[^/@]*@")

/** Removes any user:pass@ part so credentials never end up in stored or displayed URIs. */
fun stripCredentials(uri: String): String = uri.trim().replace(CREDS, "\$1")
