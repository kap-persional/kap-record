package com.kap.record

import android.content.Intent
import com.kap.record.channel.RecorderChannelHandler
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {

    private val channelHandler by lazy { RecorderChannelHandler(this) }

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
