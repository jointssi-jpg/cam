package com.sami.joocam.onvif

import com.sami.joocam.data.CameraConfig
import com.sami.joocam.data.stripCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Minimal ONVIF SOAP client (Media service). Uses WS-Security UsernameToken with
 * PasswordDigest, so the password is never sent in clear text. Nothing here logs.
 */
class OnvifClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    data class Profile(val token: String, val name: String, val width: Int?, val height: Int?) {
        val area: Int get() = (width ?: 0) * (height ?: 0)
        val resolution: String get() = if (width != null && height != null) "${width}x$height" else "?"
    }

    suspend fun getProfiles(cfg: CameraConfig): List<Profile> = withContext(Dispatchers.IO) {
        val xml = post(
            cfg, cfg.onvifMediaUrl,
            "http://www.onvif.org/ver10/media/wsdl/GetProfiles",
            "<trt:GetProfiles/>", withAuth = true,
        )
        parseProfiles(xml)
    }

    /** Asks the camera for the RTSP URI of a profile. The path is never guessed. */
    suspend fun getStreamUri(cfg: CameraConfig, profileToken: String): String = withContext(Dispatchers.IO) {
        val body = "<trt:GetStreamUri><trt:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream>" +
            "<tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trt:StreamSetup>" +
            "<trt:ProfileToken>${esc(profileToken)}</trt:ProfileToken></trt:GetStreamUri>"
        val xml = post(
            cfg, cfg.onvifMediaUrl,
            "http://www.onvif.org/ver10/media/wsdl/GetStreamUri", body, withAuth = true,
        )
        val raw = Regex("<(?:\\w+:)?Uri>([^<]+)<").find(xml)?.groupValues?.get(1)
            ?: throw IOException("Camera returned no stream URI")
        stripCredentials(raw.replace("&amp;", "&"))
    }

    // ---- internals -------------------------------------------------------------------------

    private fun parseProfiles(xml: String): List<Profile> {
        val block = Regex(
            "<(?:\\w+:)?Profiles\\b([^>]*)>(.*?)</(?:\\w+:)?Profiles>",
            RegexOption.DOT_MATCHES_ALL,
        )
        return block.findAll(xml).mapNotNull { m ->
            val attrs = m.groupValues[1]
            val body = m.groupValues[2]
            val token = Regex("token=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val name = Regex("<(?:\\w+:)?Name>([^<]*)<").find(body)?.groupValues?.get(1) ?: token
            val w = Regex("<(?:\\w+:)?Width>(\\d+)<").find(body)?.groupValues?.get(1)?.toIntOrNull()
            val h = Regex("<(?:\\w+:)?Height>(\\d+)<").find(body)?.groupValues?.get(1)?.toIntOrNull()
            Profile(token, name, w, h)
        }.toList()
    }

    /** Camera clock minus phone clock. WS-Security digests are rejected if clocks differ too much. */
    private fun clockOffsetMs(cfg: CameraConfig): Long {
        return try {
            val xml = post(
                cfg, cfg.onvifDeviceUrl,
                "http://www.onvif.org/ver10/device/wsdl/GetSystemDateAndTime",
                "<tds:GetSystemDateAndTime/>", withAuth = false,
            )
            val blk = Regex("UTCDateTime>(.*?)</(?:\\w+:)?UTCDateTime", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1) ?: return 0L
            fun n(tag: String) = Regex("<(?:\\w+:)?$tag>(\\d+)<").find(blk)!!.groupValues[1].toInt()
            val device = LocalDateTime.of(n("Year"), n("Month"), n("Day"), n("Hour"), n("Minute"), n("Second"))
                .toInstant(ZoneOffset.UTC).toEpochMilli()
            device - System.currentTimeMillis()
        } catch (e: Exception) {
            0L
        }
    }

    private fun securityHeader(cfg: CameraConfig, offsetMs: Long): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val created = Instant.ofEpochMilli(System.currentTimeMillis() + offsetMs)
            .truncatedTo(ChronoUnit.SECONDS).toString()
        val sha = MessageDigest.getInstance("SHA-1")
        sha.update(nonce)
        sha.update(created.toByteArray(Charsets.UTF_8))
        sha.update(cfg.password.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return "<wsse:Security s:mustUnderstand=\"1\" " +
            "xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" " +
            "xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">" +
            "<wsse:UsernameToken><wsse:Username>${esc(cfg.username)}</wsse:Username>" +
            "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" +
            "${b64.encodeToString(sha.digest())}</wsse:Password>" +
            "<wsse:Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">" +
            "${b64.encodeToString(nonce)}</wsse:Nonce><wsu:Created>$created</wsu:Created>" +
            "</wsse:UsernameToken></wsse:Security>"
    }

    private fun post(cfg: CameraConfig, url: String, action: String, body: String, withAuth: Boolean): String {
        val header = if (withAuth) securityHeader(cfg, clockOffsetMs(cfg)) else ""
        val envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" " +
            "xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" " +
            "xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" " +
            "xmlns:tt=\"http://www.onvif.org/ver10/schema\">" +
            "<s:Header>$header</s:Header><s:Body>$body</s:Body></s:Envelope>"
        val req = Request.Builder()
            .url(url)
            .post(envelope.toRequestBody("application/soap+xml; charset=utf-8; action=\"$action\"".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val reason = Regex("<(?:\\w+:)?Text[^>]*>([^<]*)<").find(text)?.groupValues?.get(1)
                throw IOException("ONVIF HTTP ${resp.code}" + (reason?.let { ": $it" } ?: ""))
            }
            return text
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
