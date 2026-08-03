package com.kap.record.projection

import android.content.Intent
import com.kap.record.channel.RecorderChannelHandler
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * Activity Flutter riêng dùng khi khởi động ghi hình từ Quick Settings Tile: mở thẳng vào
 * route "/countdown" (đếm ngược) thay vì route mặc định "/". Dùng FlutterEngine mới thay vì
 * engine đã cache để đơn giản hoá — đổi lại vài trăm ms khởi động engine, chấp nhận được
 * so với độ phức tạp của việc quản lý một FlutterEngine dùng chung xuyên nhiều Activity.
 */
class CountdownActivity : FlutterActivity() {

    private val channelHandler by lazy { RecorderChannelHandler(this) }

    override fun getInitialRoute(): String = "/countdown"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channelHandler.attach(flutterEngine)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        channelHandler.handleActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        channelHandler.detach()
        super.onDestroy()
    }
}
