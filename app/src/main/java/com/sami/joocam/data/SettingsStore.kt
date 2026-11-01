package com.sami.joocam.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * All camera settings, including the password, live in EncryptedSharedPreferences.
 * The AES keys are held in the Android Keystore.
 */
class SettingsStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "camera_secure",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): CameraConfig {
        val d = CameraConfig()
        return CameraConfig(
            host = prefs.getString("host", d.host) ?: d.host,
            rtspPort = prefs.getInt("rtspPort", d.rtspPort),
            onvifPort = prefs.getInt("onvifPort", d.onvifPort),
            username = prefs.getString("username", d.username) ?: d.username,
            password = prefs.getString("password", "") ?: "",
            mainUri = prefs.getString("mainUri", "") ?: "",
            subUri = prefs.getString("subUri", "") ?: "",
            auth = runCatching { AuthMethod.valueOf(prefs.getString("auth", d.auth.name)!!) }
                .getOrDefault(d.auth),
        )
    }

    fun save(c: CameraConfig) {
        prefs.edit()
            .putString("host", c.host.trim())
            .putInt("rtspPort", c.rtspPort)
            .putInt("onvifPort", c.onvifPort)
            .putString("username", c.username.trim())
            .putString("password", c.password)
            .putString("mainUri", stripCredentials(c.mainUri))
            .putString("subUri", stripCredentials(c.subUri))
            .putString("auth", c.auth.name)
            .apply()
    }
}
