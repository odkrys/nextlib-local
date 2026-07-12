package io.github.anilbeesetti.nextlib.media3ext.ffdecoder

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.anilbeesetti.nextlib.media3ext.renderer.NextTextRenderer


@UnstableApi
//open class NextRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
open class NextRenderersFactory(
    context: Context,
    private val forceDolbyVisionFallback: Boolean
) : DefaultRenderersFactory(context) {

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) return

        var extensionRendererIndex = out.size
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            extensionRendererIndex--
        }

        try {
            val renderer = FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
            out.add(extensionRendererIndex++, renderer)
            Log.i(TAG, "Loaded FfmpegAudioRenderer.")
        } catch (e: Exception) {
            // The extension is present, but instantiation failed.
            throw RuntimeException("Error instantiating Ffmpeg extension", e)
        }
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        val customCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = mediaCodecSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
                decoders
            } else {
                decoders.filter { codecInfo ->
                    if (mimeType == androidx.media3.common.MimeTypes.VIDEO_AV1) {
                        codecInfo.hardwareAccelerated || codecInfo.name.contains("dav1d", ignoreCase = true)
                    } else {
                        true
                    }
                }
            }
        }

        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            //mediaCodecSelector,
            customCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )

        val defaultRendererIndex = out.indexOfFirst { it is androidx.media3.exoplayer.video.MediaCodecVideoRenderer }

        if (defaultRendererIndex != -1) {
            val customMediaCodecRenderer = object : androidx.media3.exoplayer.video.MediaCodecVideoRenderer(
                context,
                customCodecSelector,
                allowedVideoJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
            ) {
                override fun supportsFormat(
                    mediaCodecSelector: MediaCodecSelector,
                    format: androidx.media3.common.Format
                ): Int {
                    val codecs = format.codecs
                    val isProfile7 = codecs != null && (codecs.startsWith("dvhe.07") || codecs.startsWith("dvh1.07"))

                    if (forceDolbyVisionFallback && format.sampleMimeType == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION && isProfile7) {
                        val fallbackFormat = format.buildUpon()
                            .setSampleMimeType(androidx.media3.common.MimeTypes.VIDEO_H265)
                            .setCodecs(null)
                            .build()
                        return super.supportsFormat(mediaCodecSelector, fallbackFormat)
                    }
                    return super.supportsFormat(mediaCodecSelector, format)
                }

                override fun getDecoderInfos(
                    mediaCodecSelector: MediaCodecSelector,
                    format: androidx.media3.common.Format,
                    requiresSecureDecoder: Boolean
                ): MutableList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
                    val codecs = format.codecs
                    val isProfile7 = codecs != null && (codecs.startsWith("dvhe.07") || codecs.startsWith("dvh1.07"))

                    if (forceDolbyVisionFallback && format.sampleMimeType == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION && isProfile7) {
                        Log.i(TAG, "Forcing Dolby Vision Profile 7 to HEVC for HDR10 Fallback")
                        val fallbackFormat = format.buildUpon()
                            .setSampleMimeType(androidx.media3.common.MimeTypes.VIDEO_H265)
                            .setCodecs(null)
                            .build()
                        return super.getDecoderInfos(mediaCodecSelector, fallbackFormat, requiresSecureDecoder)
                    }
                    return super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder)
                }
            }

            out.removeAt(defaultRendererIndex)
            out.add(defaultRendererIndex, customMediaCodecRenderer)
        }

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) return

        var extensionRendererIndex = out.size
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            extensionRendererIndex--
        }

        try {
            val renderer = FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
            out.add(extensionRendererIndex++, renderer)
            Log.i(TAG, "Loaded FfmpegVideoRenderer.")
        } catch (e: java.lang.Exception) {
            // The extension is present, but instantiation failed.
            throw java.lang.RuntimeException("Error instantiating Ffmpeg extension", e)
        }
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: java.util.ArrayList<Renderer>
    ) {
        out.add(NextTextRenderer(output, outputLooper))
    }

    companion object {
        const val TAG = "NextRenderersFactory"
    }
}
