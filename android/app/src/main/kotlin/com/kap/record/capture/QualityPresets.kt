package com.kap.record.capture

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import kotlin.math.roundToInt

enum class VideoQuality(
    val wireName: String,
    val longSideCap: Int,
    val bitrate: Int,
    val fps: Int
) {
    LOW("low", 1280, 2_500_000, 24),
    MEDIUM("medium", 1920, 6_000_000, 30),
    HIGH("high", 2560, 12_000_000, 30),
    ULTRA("ultra", 3840, 28_000_000, 60);

    companion object {
        fun fromWireName(name: String?): VideoQuality =
            entries.firstOrNull { it.wireName == name } ?: MEDIUM
    }
}

enum class AudioQuality(
    val wireName: String,
    val bitrate: Int,
    val sampleRate: Int
) {
    LOW("low", 96_000, 44_100),
    MEDIUM("medium", 128_000, 44_100),
    HIGH("high", 192_000, 48_000),
    ULTRA("ultra", 256_000, 48_000);

    companion object {
        fun fromWireName(name: String?): AudioQuality =
            entries.firstOrNull { it.wireName == name } ?: MEDIUM
    }
}

data class CaptureDimensions(val width: Int, val height: Int)

/**
 * Co độ phân giải thật của màn hình về đúng cạnh dài đã chọn trong cài đặt Video,
 * không phóng to nếu màn hình gốc đã nhỏ hơn mức đó. Làm tròn về số chẵn vì
 * bộ mã hoá H.264 (định dạng YUV) yêu cầu kích thước chẵn.
 */
fun computeCaptureDimensions(realWidth: Int, realHeight: Int, quality: VideoQuality): CaptureDimensions {
    val longSide = maxOf(realWidth, realHeight)
    val shortSide = minOf(realWidth, realHeight)
    val cap = quality.longSideCap
    if (longSide <= cap) {
        return CaptureDimensions(evenFloor(realWidth), evenFloor(realHeight))
    }
    val scale = cap.toDouble() / longSide.toDouble()
    val newLong = cap
    val newShort = evenFloor((shortSide * scale).roundToInt())
    return if (realWidth >= realHeight) {
        CaptureDimensions(newLong, newShort)
    } else {
        CaptureDimensions(newShort, newLong)
    }
}

private fun evenFloor(value: Int): Int = if (value % 2 == 0) value else value - 1

/**
 * Kiểm tra thiết bị có thực sự hỗ trợ mã hoá H.264 ở độ phân giải/bitrate/khung hình đã
 * chọn hay không TRƯỚC khi tạo MediaCodec. Một số thiết bị không hỗ trợ mức ULTRA
 * (4K/60fps) và trước đây sẽ ném exception khó hiểu ngay tại codec.configure().
 */
fun isVideoConfigSupported(width: Int, height: Int, quality: VideoQuality): Boolean {
    return try {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, quality.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) != null
    } catch (t: Throwable) {
        false
    }
}
