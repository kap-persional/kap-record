package com.kap.record.projection

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kap.record.Constants
import com.kap.record.service.ScreenRecordService

/**
 * Activity trong suốt, không giao diện: nơi duy nhất có thể xin sự đồng ý MediaProjection
 * khi người dùng bấm Quick Settings Tile lúc app chưa mở (TileService không tự làm được
 * việc này). Tự kiểm tra/xin quyền RECORD_AUDIO + POST_NOTIFICATIONS phòng trường hợp
 * app chưa từng được mở lần nào trước đó.
 */
class ProjectionTrampolineActivity : Activity() {

    private var permissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proceed()
    }

    private fun proceed() {
        val missing = missingRequestablePermissions()
        if (missing.isNotEmpty() && !permissionRequested) {
            permissionRequested = true
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_CODE_PERMISSIONS)
            return
        }

        // RECORD_AUDIO là bắt buộc để ghi được âm thanh nội bộ; POST_NOTIFICATIONS chỉ ảnh
        // hưởng việc hiện thông báo trạng thái nên không chặn tiếp tục ghi hình nếu thiếu.
        if (isRecordAudioMissing()) {
            Toast.makeText(
                this,
                "Cần cấp quyền Micro (Cài đặt ứng dụng KapRecord) để ghi được âm thanh nội bộ",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        requestProjection()
    }

    private fun isRecordAudioMissing(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED

    private fun missingRequestablePermissions(): List<String> {
        val list = mutableListOf<String>()
        if (isRecordAudioMissing()) {
            list.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            proceed()
        }
    }

    private fun requestProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val armIntent = Intent(this, ScreenRecordService::class.java).apply {
                    action = Constants.ACTION_ARM
                    putExtra(Constants.EXTRA_RESULT_CODE, resultCode)
                    putExtra(Constants.EXTRA_RESULT_DATA, data)
                }
                ContextCompat.startForegroundService(this, armIntent)
                startActivity(
                    Intent(this, CountdownActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            finish()
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 7001
        private const val REQUEST_CODE_PROJECTION = 7002
    }
}
