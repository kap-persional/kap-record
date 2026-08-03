package com.kap.record.capture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Bắt màn hình qua VirtualDisplay và mã hoá H.264 bằng MediaCodec (surface input).
 * Tạm dừng/tiếp tục thực hiện bằng cách tháo/gắn lại Surface của VirtualDisplay —
 * không đụng tới encoder hay muxer, nên không cần logic bù mốc thời gian thủ công.
 */
class ScreenEncoder(
    private val mediaProjection: MediaProjection,
    private val dimensions: CaptureDimensions,
    private val quality: VideoQuality,
    private val densityDpi: Int,
    private val onFormatReady: (MediaFormat) -> Int,
    private val onEncodedFrame: (trackIndex: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) -> Unit,
    private val onCodecError: (Throwable) -> Unit
) {
    private lateinit var codec: MediaCodec
    private lateinit var inputSurface: Surface
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var trackIndex = -1

    fun start() {
        handlerThread = HandlerThread("KapRecordVideoEncoder").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            dimensions.width,
            dimensions.height
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, quality.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                // Surface input: MediaCodec tự lấy khung hình từ Surface, không cần cấp buffer input thủ công.
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                try {
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (!isConfig && trackIndex >= 0) {
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null) {
                            onEncodedFrame(trackIndex, buffer, info)
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                } catch (t: Throwable) {
                    onCodecError(t)
                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                onCodecError(e)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                trackIndex = onFormatReady(format)
            }
        }, handler)

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "KapRecordScreen",
            dimensions.width,
            dimensions.height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            handler
        )
    }

    fun pause() {
        virtualDisplay?.setSurface(null)
    }

    fun resume() {
        virtualDisplay?.setSurface(inputSurface)
    }

    /** Báo hiệu kết thúc luồng — buffer EOS cuối cùng sẽ tới qua onEncodedFrame. */
    fun signalEndOfStream() {
        runCatching { codec.signalEndOfInputStream() }.onFailure { onCodecError(it) }
    }

    fun release() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { inputSurface.release() }
        handlerThread?.quitSafely()
        handlerThread = null
    }
}
