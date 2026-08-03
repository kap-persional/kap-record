package com.kap.record.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ghi âm thanh phát ra từ chính thiết bị (nhạc/video đang phát trong app khác) qua
 * AudioPlaybackCaptureConfiguration, KHÔNG dùng micro. Dấu thời gian mỗi mẫu âm thanh
 * lấy theo System.nanoTime() tại thời điểm đọc được — cùng gốc đồng hồ với dấu thời gian
 * mà VirtualDisplay/MediaCodec gắn cho khung hình video — để hai luồng không bị lệch
 * đồng bộ khi tạm dừng/tiếp tục nhiều lần.
 */
class AudioCapture(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val quality: AudioQuality,
    private val onFormatReady: (MediaFormat) -> Int,
    private val onEncodedFrame: (trackIndex: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) -> Unit,
    private val onCodecError: (Throwable) -> Unit
) {
    private val channelCount = 2
    private var audioRecord: AudioRecord? = null
    private lateinit var codec: MediaCodec
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    @Volatile private var paused = false
    @Volatile private var trackIndex = -1
    private var minBufferSize = 0

    @SuppressLint("MissingPermission")
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onCodecError(IllegalStateException("Thiếu quyền RECORD_AUDIO (bắt buộc theo API để ghi âm thanh nội bộ)"))
            return
        }

        try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val channelMask = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(quality.sampleRate)
                .setChannelMask(channelMask)
                .build()

            minBufferSize = AudioRecord.getMinBufferSize(
                quality.sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            ).let { if (it > 0) it else quality.sampleRate * 2 * channelCount }

            val record = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize * 4)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()

            // AudioRecord có thể "khởi tạo xong" (không ném exception) nhưng vẫn ở trạng thái
            // UNINITIALIZED nếu cấu hình không được thiết bị hỗ trợ — đây là lỗi rất dễ bị bỏ sót
            // vì startRecording()/read() sau đó không nhất thiết báo lỗi rõ ràng, dẫn tới ghi hình
            // "chạy bình thường" nhưng không có mẫu âm thanh nào thực sự được đưa vào encoder.
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                onCodecError(IllegalStateException("Không thể khởi tạo AudioRecord để ghi âm thanh nội bộ (thiết bị có thể không hỗ trợ)"))
                return
            }
            audioRecord = record

            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                quality.sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrate)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                onCodecError(IllegalStateException("AudioRecord không chuyển sang trạng thái đang ghi được"))
                return
            }
        } catch (t: Throwable) {
            onCodecError(t)
            return
        }

        running.set(true)
        paused = false

        thread = Thread({ runLoop() }, "KapRecordAudioEncoder").apply { start() }
    }

    fun pause() {
        paused = true
        runCatching { audioRecord?.stop() }
    }

    fun resume() {
        runCatching { audioRecord?.startRecording() }
        paused = false
    }

    /** Yêu cầu dừng — vòng lặp nền sẽ tự đẩy EOS qua encoder rồi thoát. */
    fun stop() {
        running.set(false)
    }

    fun awaitFinished(timeoutMs: Long = 4000) {
        thread?.join(timeoutMs)
    }

    fun release() {
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    private fun runLoop() {
        val pcmBuffer = ByteArray(minBufferSize)
        val startNanos = System.nanoTime()
        var consecutiveErrors = 0
        try {
            while (running.get()) {
                if (paused) {
                    Thread.sleep(20)
                    continue
                }
                val record = audioRecord ?: break
                val read = record.read(pcmBuffer, 0, pcmBuffer.size)
                val captureTimeUs = (System.nanoTime() - startNanos) / 1000
                if (read > 0) {
                    consecutiveErrors = 0
                    feedInput(pcmBuffer, read, captureTimeUs, endOfStream = false)
                } else if (read < 0) {
                    // read() trả về mã lỗi âm (ERROR_INVALID_OPERATION, ERROR_DEAD_OBJECT...).
                    // Vài lần đầu có thể chỉ là tạm thời, nhưng nếu lặp lại liên tục nghĩa là
                    // AudioRecord thực sự hỏng — phải abort thay vì âm thầm ghi ra file trống.
                    consecutiveErrors++
                    if (consecutiveErrors >= 50) {
                        onCodecError(IllegalStateException("AudioRecord liên tục lỗi khi đọc (mã $read) — dừng ghi âm thanh nội bộ"))
                        return
                    }
                    Thread.sleep(20)
                }
                drainOutput(endOfStream = false)
            }
            feedInput(pcmBuffer, 0, (System.nanoTime() - startNanos) / 1000, endOfStream = true)
            drainOutput(endOfStream = true)
        } catch (t: Throwable) {
            onCodecError(t)
        }
    }

    private fun feedInput(data: ByteArray, length: Int, presentationTimeUs: Long, endOfStream: Boolean) {
        val index = codec.dequeueInputBuffer(10_000)
        if (index < 0) return
        val inputBuffer = codec.getInputBuffer(index) ?: return
        inputBuffer.clear()
        if (length > 0) {
            inputBuffer.put(data, 0, length)
        }
        val flags = if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        codec.queueInputBuffer(index, 0, length, presentationTimeUs, flags)
    }

    private fun drainOutput(endOfStream: Boolean) {
        val bufferInfo = MediaCodec.BufferInfo()
        var safetyIterations = 0
        while (true) {
            if (endOfStream && safetyIterations++ > 500) {
                onCodecError(IllegalStateException("Hết thời gian chờ AAC encoder kết thúc luồng"))
                return
            }
            val index = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                index >= 0 -> {
                    if (trackIndex >= 0) {
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig) {
                            val outBuffer = codec.getOutputBuffer(index)
                            if (outBuffer != null) {
                                onEncodedFrame(trackIndex, outBuffer, bufferInfo)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        return
                    }
                    if (!endOfStream) return
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = onFormatReady(codec.outputFormat)
                }
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                else -> return
            }
        }
    }
}
