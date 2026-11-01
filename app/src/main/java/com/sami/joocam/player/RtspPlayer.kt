package com.sami.joocam.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

sealed interface PlayerEvent {
    data object Playing : PlayerEvent
    data class Buffering(val percent: Float) : PlayerEvent
    data object Error : PlayerEvent
    data object Ended : PlayerEvent
}

/**
 * Thin wrapper around LibVLC. Playback only: recording, AI and uploads will be separate
 * components in later milestones.
 *
 * Credentials are passed as VLC media options, never inside the URL.
 */
class RtspPlayer(context: Context) {

    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf("--rtsp-tcp", "--network-caching=400"),
    )
    private val mp = MediaPlayer(libVlc)

    var listener: ((PlayerEvent) -> Unit)? = null

    init {
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> listener?.invoke(PlayerEvent.Playing)
                MediaPlayer.Event.Buffering -> listener?.invoke(PlayerEvent.Buffering(e.buffering))
                MediaPlayer.Event.EncounteredError -> listener?.invoke(PlayerEvent.Error)
                MediaPlayer.Event.EndReached -> listener?.invoke(PlayerEvent.Ended)
            }
        }
    }

    fun attach(layout: VLCVideoLayout) {
        // useTextureView = true so snapshots can be read from the TextureView.
        mp.attachViews(layout, null, false, true)
        mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
    }

    fun detach() {
        mp.detachViews()
    }

    fun play(uri: String, user: String?, password: String?, muted: Boolean) {
        val old = mp.media
        mp.stop()
        old?.release()

        val media = Media(libVlc, Uri.parse(uri))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=400")
        media.addOption(":rtsp-tcp")
        if (!user.isNullOrEmpty()) {
            media.addOption(":rtsp-user=$user")
            media.addOption(":rtsp-pwd=${password.orEmpty()}")
        }
        mp.media = media
        media.release()
        mp.volume = if (muted) 0 else 100
        mp.play()
    }

    fun stop() {
        mp.stop()
    }

    fun setMuted(muted: Boolean) {
        mp.volume = if (muted) 0 else 100
    }

    /** Grabs the current video frame from the TextureView inside the layout, or null. */
    fun captureFrame(layout: View?): Bitmap? {
        val tv = findTextureView(layout) ?: return null
        return if (tv.isAvailable) tv.bitmap else null
    }

    private fun findTextureView(v: View?): TextureView? {
        if (v is TextureView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) findTextureView(v.getChildAt(i))?.let { return it }
        }
        return null
    }

    fun release() {
        mp.stop()
        mp.release()
        libVlc.release()
    }
}
