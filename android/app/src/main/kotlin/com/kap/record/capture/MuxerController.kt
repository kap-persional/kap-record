package com.kap.record.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.nio.ByteBuffer

/**
 * Gói MediaMuxer: chỉ start() khi CẢ HAI track video và audio đã có định dạng
 * (2 luồng khác nhau gọi vào, nên mọi thao tác đều synchronized). Mỗi track tự
 * trừ đi mốc thời gian (timestamp) của mẫu đầu tiên của chính nó — độ lệch cố định
 * giữa 2 mốc bắt đầu (thường vài chục ms do thứ tự khởi động encoder) là đánh đổi
 * chấp nhận được để đổi lấy việc không phải đồng bộ epoch phức tạp giữa 2 luồng.
 */
class MuxerController(pfd: ParcelFileDescriptor) {
    private val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val lock = Any()

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    @Volatile private var started = false
    @Volatile private var released = false
    private var videoBaseUs = -1L
    private var audioBaseUs = -1L
    private var maxPresentationUs = 0L

    @Volatile var videoDone = false
        private set

    @Volatile var audioDone = false
        private set

    fun addVideoTrack(format: MediaFormat): Int = synchronized(lock) {
        videoTrackIndex = muxer.addTrack(format)
        maybeStart()
        videoTrackIndex
    }

    fun addAudioTrack(format: MediaFormat): Int = synchronized(lock) {
        audioTrackIndex = muxer.addTrack(format)
        maybeStart()
        audioTrackIndex
    }

    private fun maybeStart() {
        if (!started && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
            muxer.start()
            started = true
        }
    }

    fun writeSample(trackIndex: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (released || trackIndex < 0) return
            val isVideo = trackIndex == videoTrackIndex
            val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

            if (started && info.size > 0) {
                var base = if (isVideo) videoBaseUs else audioBaseUs
                if (base < 0) {
                    base = info.presentationTimeUs
                    if (isVideo) videoBaseUs = base else audioBaseUs = base
                }
                val relativeUs = maxOf(0L, info.presentationTimeUs - base)
                val muxerFlags = info.flags and MediaCodec.BUFFER_FLAG_SYNC_FRAME
                val adjusted = MediaCodec.BufferInfo().apply {
                    set(info.offset, info.size, relativeUs, muxerFlags)
                }
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                runCatching { muxer.writeSampleData(trackIndex, buffer, adjusted) }
                    .onFailure { Log.e(TAG, "writeSampleData lỗi (track=$trackIndex, size=${info.size})", it) }
                if (relativeUs > maxPresentationUs) maxPresentationUs = relativeUs
            }

            if (isEos) {
                if (isVideo) videoDone = true else audioDone = true
            }
        }
    }

    fun isFullyDone(): Boolean = synchronized(lock) { videoDone && audioDone }

    /** true khi MediaMuxer.start() đã thực sự được gọi (cả 2 track đã sẵn sàng). */
    fun hasStarted(): Boolean = synchronized(lock) { started }

    fun durationMs(): Long = maxPresentationUs / 1000

    fun finalizeAndRelease() {
        synchronized(lock) {
            if (released) return
            released = true
            if (started) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    companion object {
        private const val TAG = "MuxerController"
    }
}
