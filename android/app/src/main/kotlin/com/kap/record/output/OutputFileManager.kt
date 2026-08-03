package com.kap.record.output

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.kap.record.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface RecordingOutputTarget {
    val uri: Uri
    val displayName: String

    /** Mở file descriptor để MediaMuxer ghi trực tiếp vào. */
    fun openFileDescriptor(context: Context): ParcelFileDescriptor

    /** Đóng file, hoàn tất (bỏ cờ IS_PENDING nếu là MediaStore). Trả về dung lượng file (bytes). */
    fun finalizeOutput(context: Context, lastKnownSize: Long): Long

    /** Xoá file dở dang khi người dùng huỷ hoặc có lỗi xảy ra giữa chừng. */
    fun deleteIfIncomplete(context: Context)
}

private class MediaStoreOutputTarget(
    override val uri: Uri,
    override val displayName: String
) : RecordingOutputTarget {

    override fun openFileDescriptor(context: Context): ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, "rw")
            ?: error("Không thể mở file đầu ra trong MediaStore")

    override fun finalizeOutput(context: Context, lastKnownSize: Long): Long {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
        return lastKnownSize
    }

    override fun deleteIfIncomplete(context: Context) {
        context.contentResolver.delete(uri, null, null)
    }
}

private class SafOutputTarget(
    override val uri: Uri,
    override val displayName: String
) : RecordingOutputTarget {

    override fun openFileDescriptor(context: Context): ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, "rw")
            ?: error("Không thể mở file đầu ra trong thư mục đã chọn")

    override fun finalizeOutput(context: Context, lastKnownSize: Long): Long = lastKnownSize

    override fun deleteIfIncomplete(context: Context) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }
}

object OutputFileManager {

    const val SAVE_MODE_GALLERY = "gallery"
    const val SAVE_MODE_CUSTOM = "custom"

    fun buildFileName(): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "KAP_${fmt.format(Date())}.mp4"
    }

    fun createTarget(context: Context, saveMode: String?, customTreeUri: String?): RecordingOutputTarget {
        val fileName = buildFileName()
        return if (saveMode == SAVE_MODE_CUSTOM && !customTreeUri.isNullOrEmpty()) {
            createSafTarget(context, Uri.parse(customTreeUri), fileName)
        } else {
            createMediaStoreTarget(context, fileName)
        }
    }

    private fun createMediaStoreTarget(context: Context, fileName: String): RecordingOutputTarget {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, Constants.MIME_TYPE_MP4)
            put(MediaStore.Video.Media.RELATIVE_PATH, Constants.MOVIES_RELATIVE_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values)
            ?: error("Không thể tạo file trong thư viện ảnh (MediaStore)")
        return MediaStoreOutputTarget(uri, "${Constants.MOVIES_RELATIVE_PATH}/$fileName")
    }

    private fun createSafTarget(context: Context, treeUri: Uri, fileName: String): RecordingOutputTarget {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: error("Thư mục lưu không hợp lệ")
        val file = tree.createFile(Constants.MIME_TYPE_MP4, fileName)
            ?: error("Không thể tạo file trong thư mục đã chọn")
        return SafOutputTarget(file.uri, fileName)
    }
}
