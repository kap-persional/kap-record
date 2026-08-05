# KapRecord — Wiki Kỹ Thuật

## Tổng quan

Ứng dụng Flutter **Android-only** ghi màn hình kèm âm thanh nội bộ (âm thanh đang phát ra từ thiết bị, không phải mic). Xuất file MP4 nén, lưu vào Gallery hoặc thư mục tùy chọn.

**Package / applicationId**: `com.kap.record`  
**minSdkVersion**: 29 (Android 10 — bắt buộc vì `AudioPlaybackCaptureConfiguration` chỉ có từ API 29)  
**Gradle**: 8.11.1 | **AGP**: 8.7.2 | **Kotlin**: 2.0.21 | **Flutter**: stable (3.44+)  
**Branch làm việc**: `dev` (phát triển hàng ngày, push tự do) — `main` chỉ merge khi người dùng yêu cầu rõ ràng ("merge", "build"). Xem quy tắc đầy đủ trong `CLAUDE.md`.

---

## Kiến trúc tổng thể

```
Flutter (Dart)                          Android Native (Kotlin)
──────────────────────────────────────────────────────────────────
RecorderChannel (MethodChannel/EventChannel)
    │                                       │
    ▼                                       ▼
RecorderProvider (ChangeNotifier)   RecorderChannelHandler
    │                                       │ bindService (async)
    ▼                                       ▼
UI Screens / Widgets                ScreenRecordService (ForegroundService)
                                            │
                                    ┌───────┼───────┐
                                    ▼       ▼       ▼
                               ScreenEncoder  AudioCapture  MuxerController
                               (VirtualDisplay  (AudioRecord  (MediaMuxer
                               + H.264 codec)  + AAC codec)   MP4 output)
```

**Nguyên tắc**: ScreenRecordService là nguồn sự thật duy nhất về trạng thái ghi hình. Trạng thái được persist vào SharedPreferences (qua `RecordingStateHolder`) để TileService và các component khác đọc được ngay cả khi Flutter engine chưa khởi động.

---

## Cấu trúc thư mục

### Flutter (`lib/`)

| File | Mô tả |
|---|---|
| `main.dart` | Khởi tạo app, wire providers |
| `app.dart` | MaterialApp, routes, theme |
| `models/recording_state.dart` | `RecordingSnapshot`, `RecorderPhase`, `StopResult` |
| `models/quality_preset.dart` | `VideoQualityPreset`, `AudioQualityPreset` (4 mức mỗi loại) |
| `models/save_location.dart` | `SaveFolderResult` |
| `services/recorder_channel.dart` | Bọc MethodChannel + EventChannel, singleton `RecorderChannel.instance` |
| `services/settings_service.dart` | Đọc/ghi SharedPreferences cho cài đặt |
| `providers/recorder_provider.dart` | `RecorderProvider` (ChangeNotifier), lắng nghe EventChannel, tự resubscribe khi stream lỗi |
| `providers/settings_provider.dart` | `SettingsProvider` (ChangeNotifier), persist qua `SettingsService` |
| `screens/home_screen.dart` | Màn hình chính: nút Quay/Tạm dừng/Tiếp tục/Dừng, đồng hồ, banner cảnh báo audio im lặng |
| `screens/settings_screen.dart` | Chọn chất lượng video/âm thanh, thư mục lưu, đếm ngược |
| `screens/countdown_screen.dart` | Màn hình đếm ngược (route `/countdown`) |
| `screens/onboarding_permissions_screen.dart` | Màn hình giải thích + xin quyền lần đầu |
| `widgets/record_button.dart` | `RecordButton` hiển thị đúng trạng thái |
| `widgets/timer_display.dart` | Đồng hồ hiển thị thời gian đã ghi |
| `widgets/countdown_overlay.dart` | Overlay đếm ngược |

### Android Native (`android/app/src/main/kotlin/com/kap/record/`)

| File | Mô tả |
|---|---|
| `MainActivity.kt` | FlutterActivity chính, gắn `RecorderChannelHandler` |
| `Constants.kt` | Hằng số channel, action, preference key. Có extension `Throwable.describeForUser()` |
| `projection/ProjectionTrampolineActivity.kt` | Activity trong suốt, xin quyền MediaProjection từ Tile |
| `projection/CountdownActivity.kt` | FlutterActivity riêng, `initialRoute = "/countdown"` khi mở từ Tile |
| `tile/RecordTileService.kt` | Quick Settings Tile (IDLE→ARMED/RECORDING→IDLE) |
| `service/ScreenRecordService.kt` | Foreground service, state machine chính, watchdog theo session token, DisplayListener xoay màn hình, cleanup khi bị OS/OEM kill |
| `capture/ScreenEncoder.kt` | VirtualDisplay + H.264 MediaCodec (surface input, async callback), `resize()` khi xoay màn hình |
| `capture/AudioCapture.kt` | `AudioPlaybackCaptureConfiguration` + AudioRecord + AAC MediaCodec, phát hiện PCM gần như im lặng |
| `capture/MuxerController.kt` | MediaMuxer, đồng bộ 2 track trước khi start() |
| `capture/QualityPresets.kt` | `VideoQuality`, `AudioQuality` enums + `computeCaptureDimensions()` + `isVideoConfigSupported()` |
| `output/OutputFileManager.kt` | Tạo file qua MediaStore (Gallery) hoặc SAF (thư mục tùy chọn) |
| `state/RecordingState.kt` | Enum: IDLE, ARMED, RECORDING, PAUSED, STOPPING |
| `state/RecordingStateHolder.kt` | Singleton trạng thái, listener pattern, persist SharedPreferences, cờ `audioLikelySilent` |
| `notification/RecordingNotification.kt` | Xây dựng Notification (ARMED, RECORDING, PAUSED) với các nút hành động |
| `channel/RecorderChannelHandler.kt` | Xử lý MethodChannel/EventChannel, bindService với retry |

---

## Luồng ghi hình — State Machine

```
IDLE
  │─── bấm Quay (từ app) ──► requestConsent() → hộp thoại MediaProjection
  │    hoặc bấm Tile ──────► ProjectionTrampolineActivity → hộp thoại
  │
  │    Nếu đồng ý:
  │    ACTION_ARM → ScreenRecordService.startForeground() + markArmed()
  │
  ▼
ARMED
  │─── startRecording() ─────► encoder.start() + audio.start()
  │                             scheduleStartWatchdog(5s, sessionId)
  ▼
RECORDING ◄──────────────────────────────────────────────┐
  │                                                       │
  ├── pause() ──────► virtualDisplay.setSurface(null)     │
  │                   audioRecord.stop()                  │
  ▼                                                       │
PAUSED                                                    │
  │                                                       │
  └── resume() ────► virtualDisplay.setSurface(surface) ──┘
                     audioRecord.startRecording()

RECORDING / PAUSED
  │─── stop() ────► encoder.signalEndOfInputStream()
  │                 audio.stop() → awaitFinished()
  │                 waitForVideoDrain()
  ▼
STOPPING
  │─── finalizeRecording()
  │     - nếu muxer.hasStarted() → muxer.stop() + finalizeOutput() → RESULT
  │     - nếu !muxer.hasStarted() → deleteFile + error message
  ▼
IDLE
```

---

## Chi tiết từng thành phần quan trọng

### ScreenEncoder (`capture/ScreenEncoder.kt`)

- Dùng **async callback mode** (`MediaCodec.setCallback()`) trên `HandlerThread "KapRecordVideoEncoder"`
- Surface input: MediaCodec tự lấy khung hình từ VirtualDisplay, không cần cấp buffer input thủ công
- **Tạm dừng**: `virtualDisplay.setSurface(null)` — encoder vẫn sống, muxer tiếp tục nhận frame sau resume
- **Dừng**: `codec.signalEndOfInputStream()` → chờ buffer EOS qua callback → `ScreenRecordService.waitForVideoDrain()`
- **Xoay màn hình khi đang ghi**: `resize(width, height, densityDpi)` gọi `virtualDisplay.resize(...)` — chỉ đổi kích thước logic của display, KHÔNG đổi kích thước Surface/codec (MediaCodec surface-input không hỗ trợ đổi format sau `configure()`). Nếu tỷ lệ khung hình đổi (dọc↔ngang) video có thể bị méo/co kéo, nhưng không crash.
- Release: hủy VirtualDisplay trước → `handlerThread.quitSafely()` + `join(500)` (đợi HandlerThread xử lý xong các callback đã xếp hàng và thoát hẳn) → rồi mới `codec.stop()` + `codec.release()` + `inputSurface.release()`. Thứ tự này (trước đây khác) tránh race giữa luồng callback `onOutputBufferAvailable` và `stop()/release()` chạy trên luồng khác.

### AudioCapture (`capture/AudioCapture.kt`)

- Thread nền `"KapRecordAudioEncoder"` chạy vòng lặp đọc PCM → feed vào AAC MediaCodec → drainOutput
- **Lỗi thường gặp**: `AudioRecord.build()` không ném exception nhưng ở `STATE_UNINITIALIZED` → kiểm tra state ngay sau build
- Timestamp lấy từ `System.nanoTime()` (cùng đồng hồ với VirtualDisplay/MediaCodec)
- Detect lỗi liên tục: `consecutiveErrors >= 50` → gọi `onCodecError()`
- **Tạm dừng**: `audioRecord.stop()` (không dừng thread hay codec)
- **Dừng**: `running.set(false)` → vòng lặp tự thoát, gửi EOS qua codec rồi drain
- **`awaitFinished(timeoutMs)`**: trả về `Boolean` (trước đây `Unit`) — `true` nếu luồng nền thực sự thoát trong thời gian chờ
- **`release()`**: nếu `thread?.isAlive == true` (luồng nền chưa thoát dù đã `stop()` + `awaitFinished()`, ví dụ `AudioRecord.read()` kẹt trên driver OEM lỗi) → **bỏ qua release hoàn toàn**, không đụng `codec`/`audioRecord` — tránh race/crash với `runLoop()` vẫn đang chạy trên luồng khác. Đánh đổi: rò rỉ tài nguyên nhỏ trong trường hợp hiếm này, chấp nhận được so với crash.
- **Phát hiện audio gần như im lặng**: trong 5 giây đầu (`SILENCE_CHECK_WINDOW_US`, cùng mốc với watchdog), tính biên độ PCM 16-bit trung bình; nếu dưới ngưỡng (`SILENCE_AMPLITUDE_THRESHOLD = 50`) → gọi `onSilentAudioDetected()` (khác `onCodecError`, KHÔNG huỷ buổi ghi, chỉ cảnh báo — xem mục "Cảnh báo audio im lặng" bên dưới).

### MuxerController (`capture/MuxerController.kt`)

- MediaMuxer chỉ `start()` khi **cả hai** track (video + audio) đã `addTrack()` xong
- `writeSample()` bỏ qua nếu `!started` → tất cả frame bị lặng lẽ bỏ qua nếu audio không sẵn sàng; nếu `muxer.writeSampleData()` tự nó lỗi (dù `started`), lỗi được log rõ (`Log.e`) thay vì nuốt hoàn toàn im lặng như trước — hành vi vẫn là bỏ qua sample đó và tiếp tục, chỉ thêm log chẩn đoán
- Mỗi track tự trừ đi timestamp của mẫu đầu tiên → relative timestamp
- **`hasStarted()`**: public getter để watchdog và finalizeRecording kiểm tra
- **`videoDone`**: flag cho `waitForVideoDrain()` ở ScreenRecordService biết khi nào drain xong

### Watchdog (trong `ScreenRecordService`)

- Sau 5 giây kể từ khi `encoder.start()` + `audio.start()`, kiểm tra `muxer.hasStarted()`
- Nếu muxer chưa start → `cleanupAfterFailure(reason)` → huỷ file dở dang, reset về IDLE với thông báo lỗi rõ ràng
- Mục đích: ngăn ghi hình "âm thầm" không có dữ liệu rồi tạo file hỏng
- **Session token**: mỗi lần `startRecordingRequest()` chạy, `recordingSessionId` tăng 1 và watchdog capture giá trị đó. Watchdog chỉ hành động nếu `sessionId == recordingSessionId` hiện tại — tránh trường hợp dừng rồi bắt đầu lại rất nhanh (trong vòng 5s) khiến watchdog của buổi ghi cũ huỷ nhầm buổi ghi mới đang chạy tốt.
- **`cleanupAfterFailure()`** giờ chờ `waitForVideoDrain()` (giống hệt luồng dừng thành công) trước khi release encoder trên background thread, thay vì chỉ `Thread.sleep(100)` cố định như trước — tránh release encoder khi callback MediaCodec vẫn còn xử lý buffer cuối.

### Xoay màn hình khi đang ghi

`ScreenRecordService` đăng ký `DisplayManager.DisplayListener` khi bắt đầu ghi (`registerRotationListener()`), huỷ đăng ký khi dừng/onDestroy. Khi phát hiện `Display.DEFAULT_DISPLAY` đổi trong lúc RECORDING/PAUSED, tính lại `computeCaptureDimensions()` theo kích thước màn hình mới và gọi `ScreenEncoder.resize()`. Đây là fix "không crash khi xoay máy" — KHÔNG đảm bảo video luôn đúng tỷ lệ hoàn hảo ở mọi hướng xoay (xem [[#Vấn đề còn tồn tại / Chưa xác nhận]]).

### Kiểm tra thiết bị hỗ trợ chất lượng video (`isVideoConfigSupported`)

Trước khi tạo `ScreenEncoder`, `startRecordingRequest()` gọi `isVideoConfigSupported(width, height, quality)` (dùng `MediaCodecList.findEncoderForFormat()`) để xác nhận thiết bị hỗ trợ cấu hình H.264 đã chọn — đặc biệt quan trọng với mức ULTRA (4K/60fps). Nếu không hỗ trợ, trả lỗi rõ ràng cho Flutter ("hãy chọn mức chất lượng thấp hơn") thay vì để crash tại `codec.configure()`.

### Cảnh báo audio im lặng (`audioLikelySilent`)

`RecordingStateHolder.audioLikelySilent` (Boolean) là cờ cảnh báo — KHÔNG phải lỗi, KHÔNG huỷ buổi ghi — được set `true` khi `AudioCapture` phát hiện PCM gần như im lặng trong 5 giây đầu buổi ghi. Đẩy qua EventChannel cùng `state/elapsedSeconds/isPaused/error`, Flutter hiện banner cam trên `HomeScreen` khi cờ này `true` và đang RECORDING/PAUSED. Đây là **heuristic giảm nhẹ**, không phải fix tận gốc, cho vấn đề "âm thanh nội bộ không ghi được trên một số máy Samsung/OEM" (xem mục Vấn đề còn tồn tại).

### RecorderChannelHandler — bindService retry (`channel/RecorderChannelHandler.kt`)

- `withBoundService(result, retriesLeft=20, action)`: poll 20×100ms trước khi báo `SERVICE_NOT_READY`
- Lý do: `bindService()` là async — nếu người dùng bắt đầu ghi từ Tile rồi mở app và bấm Dừng ngay, `boundService` có thể còn null
- Pause/Resume/Cancel dùng `sendServiceAction()` (startService Intent) vì không cần kết quả trả về

### Quick Settings Tile — luồng bắt đầu từ Tile

1. Người dùng bấm Tile → `RecordTileService.onClick()` → `launchTrampoline()` → `ProjectionTrampolineActivity`
2. `ProjectionTrampolineActivity`:
   - Kiểm tra `RECORD_AUDIO` + `POST_NOTIFICATIONS` → xin nếu thiếu
   - Nếu `RECORD_AUDIO` bị từ chối vĩnh viễn → Toast giải thích + `finish()`
   - Xin đồng ý MediaProjection → `ACTION_ARM` → `ScreenRecordService.startForeground()`
   - Mở `CountdownActivity` (route `/countdown`) + `finish()`
3. `CountdownActivity` = FlutterActivity riêng với `initialRoute="/countdown"`, có `RecorderChannelHandler` riêng bind vào cùng ScreenRecordService

### Lưu file — hai chế độ (`output/OutputFileManager.kt`)

**Gallery (mặc định)**:
- `MediaStore.Video.Media.insert()` với `IS_PENDING=1`
- Ghi qua `ParcelFileDescriptor` trực tiếp vào MediaStore entry
- Khi xong: `update(IS_PENDING=0)` → file hiện trong Gallery ngay
- Xoá khi lỗi: `contentResolver.delete(uri)`

**Thư mục tùy chọn (SAF)**:
- Chọn qua `ACTION_OPEN_DOCUMENT_TREE` → `takePersistableUriPermission()` (quyền lâu dài)
- Tạo file qua `DocumentFile.createFile()`
- Xoá khi lỗi: `DocumentsContract.deleteDocument()`

---

## Cài đặt chất lượng

### Video (`VideoQuality`)

| Mức | wireName | Cạnh dài | Bitrate | FPS |
|---|---|---|---|---|
| Thấp | `low` | 1280px | 2.5 Mbps | 24 |
| Trung bình (mặc định) | `medium` | 1920px | 6 Mbps | 30 |
| Cao | `high` | 2560px | 12 Mbps | 30 |
| Cực cao | `ultra` | 3840px | 28 Mbps | 60 |

`computeCaptureDimensions()`: co kích thước màn hình về đúng `longSideCap`, giữ tỷ lệ, làm tròn về số chẵn.

### Âm thanh (`AudioQuality`)

| Mức | wireName | Bitrate AAC | Sample rate |
|---|---|---|---|
| Thấp | `low` | 96 kbps | 44.1kHz |
| Trung bình (mặc định) | `medium` | 128 kbps | 44.1kHz |
| Cao | `high` | 192 kbps | 48kHz |
| Cực cao | `ultra` | 256 kbps | 48kHz |

---

## Quyền và luồng xin quyền

| Quyền | Bắt buộc | Lý do |
|---|---|---|
| `RECORD_AUDIO` | Có | Android yêu cầu để dùng `AudioPlaybackCaptureConfiguration`, dù app không ghi mic |
| `FOREGROUND_SERVICE` | Có | Chạy foreground service |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Có (API 34+) | foregroundServiceType="mediaProjection" |
| `POST_NOTIFICATIONS` | Không bắt buộc | Hiển thị thông báo trạng thái (API 33+) |

**Luồng xin quyền**:
1. Onboarding (lần đầu mở app): xin `RECORD_AUDIO` + `POST_NOTIFICATIONS` — chỉ giải thích, không chặn nếu từ chối
2. `HomeScreen._ensureMicrophonePermission()`: kiểm tra/xin `RECORD_AUDIO` **trước** khi xin MediaProjection — nếu từ chối vĩnh viễn → dialog với nút "Mở Cài đặt"
3. MediaProjection: xin mỗi lần bấm Quay (hạn chế của Android, không thể cache)
4. Tile (`ProjectionTrampolineActivity`): tự kiểm tra quyền, Toast nếu bị từ chối vĩnh viễn

---

## MethodChannel & EventChannel

**Channel names**:
- Method: `com.kap.record/recorder`
- Event: `com.kap.record/recorder_events`

**Methods (Dart → Native)**:

| Method | Params | Return |
|---|---|---|
| `requestProjectionConsent` | — | `bool` (đồng ý hay không) |
| `startRecording` | `videoQuality`, `audioQuality`, `saveMode`, `customUri` | void hoặc error |
| `pauseRecording` | — | void |
| `resumeRecording` | — | void |
| `stopRecording` | — | `{uri, displayPath, durationMs, sizeBytes}` hoặc error |
| `cancelArmed` | — | void |
| `pickSaveFolder` | — | `{uri, displayName}` hoặc null |
| `getCurrentState` | — | state map |

**EventChannel (Native → Dart)**: đẩy `{state, elapsedSeconds, isPaused, error, audioLikelySilent}` mỗi khi trạng thái thay đổi (và mỗi giây khi đang ghi). `audioLikelySilent` là cờ cảnh báo heuristic — xem mục "Cảnh báo audio im lặng" ở trên.

---

## CI/CD — GitHub Actions

File: `.github/workflows/build.yml`

**Job 1: `build`**
1. `flutter doctor -v`
2. `flutter pub get`
3. `flutter analyze` — phải xanh
4. `flutter build apk --debug`
5. Upload artifact `kap-record-debug-apk` (14 ngày)

**Job 2: `emulator-smoke-test`** (phụ thuộc job 1)
1. Bật KVM cho GitHub Linux runner
2. Download APK từ job 1
3. `reactivecircus/android-emulator-runner@v2` (API 30, google_apis, x86_64, pixel_5)
4. Chạy `.github/scripts/emulator_smoke_test.sh`:
   - `adb install`, `am start`, `sleep 10`, kiểm tra `pidof` — nếu không tìm thấy process → exit 1
5. Upload `logcat.txt` (kể cả khi lỗi)

**Lưu ý**: Emulator **không mô phỏng** `AudioPlaybackCaptureConfiguration` đúng — chỉ smoke test crash/startup, không xác nhận âm thanh nội bộ. Không xác nhận được cảnh báo audio-im-lặng, xoay màn hình khi ghi, hay hành vi khi OS/OEM kill service — các phần này cần test tay trên thiết bị thật.

### Trigger — CHỈ build tự động trên `main`

`build.yml` cấu hình `on.push.branches: ['main']` — **push vào `dev` KHÔNG tự trigger CI**. Đây là quyết định có chủ đích: `dev` là nơi commit lặt vặt hàng ngày, build APK mỗi lần push sẽ tốn phút CI vô ích. CI chỉ tự chạy khi có push vào `main`, tức là khi người dùng yêu cầu rõ ràng "merge"/"build" và merge `dev` → `main` (xem quy tắc branch trong `CLAUDE.md` — KHÔNG tự ý merge vào `main` khi chưa được yêu cầu).

Muốn build thử trên `dev` mà chưa merge vào `main`: chạy tay qua `workflow_dispatch` (tab Actions → chọn workflow → Run workflow → chọn branch `dev`), không sửa lại trigger `push` về `dev`.

**Trạng thái build gần nhất đã xác nhận** (05/08/2026, commit `623eea91c...` merge vào `main`): `flutter analyze` xanh, `flutter build apk --debug` thành công, `emulator-smoke-test` xác nhận app mở lên không crash. Ghi âm thanh nội bộ thật trên thiết bị vẫn cần test tay (xem mục "Vấn đề còn tồn tại" bên dưới).

---

## Lịch sử lỗi đã sửa

### Lỗi 1: Nút Dừng không phản hồi
**Nguyên nhân**: Người dùng bắt đầu ghi từ Tile → mở app → bấm Dừng ngay khi `boundService` còn null (bindService async).  
**Sửa**: `RecorderChannelHandler.withBoundService()` poll 20×100ms trước khi báo lỗi.

### Lỗi 2: Video ghi xong không xem được (file hỏng)
**Nguyên nhân**: `AudioRecord.build()` không ném exception nhưng ở `STATE_UNINITIALIZED` → không bao giờ gọi `onFormatReady()` → `MuxerController.start()` không bao giờ chạy → tất cả frame video bị bỏ qua âm thầm → file trống.  
**Sửa**:
- `AudioCapture`: kiểm tra `audioRecord.state == STATE_INITIALIZED` sau build
- `MuxerController`: thêm `hasStarted()` public getter
- `ScreenRecordService`: watchdog 5 giây → `cleanupAfterFailure()` nếu muxer chưa start
- `finalizeRecording()`: nếu `!muxer.hasStarted()` → xóa file + error message rõ ràng

### Lỗi 3: Thiếu quyền RECORD_AUDIO khi bắt đầu ghi
**Nguyên nhân**: Onboarding xin quyền nhưng không chặn nếu từ chối, và không có bước nào kiểm tra lại trước khi ghi.  
**Sửa**: `HomeScreen._ensureMicrophonePermission()` kiểm tra/xin trước `requestConsent()`.

### Lỗi 4: Toast khi Tile bị từ chối vĩnh viễn
**Nguyên nhân**: `ProjectionTrampolineActivity` lặng lẽ `finish()` khi thiếu `RECORD_AUDIO`.  
**Sửa**: Hiện `Toast.makeText(..., LONG).show()` trước `finish()`.

### Lỗi 5: Thông báo lỗi không rõ ("Lỗi không xác định")
**Nguyên nhân**: Dùng `t.message ?: "Lỗi không xác định"` không kèm tên class exception.  
**Sửa**: Extension `Throwable.describeForUser()` trong `Constants.kt` luôn kèm tên class.

### Lỗi CI: Shell syntax error trong emulator smoke test
**Nguyên nhân**: `reactivecircus/android-emulator-runner` chạy mỗi dòng YAML `script:` như `sh -c` riêng biệt → `if/then/fi` multi-line không hoạt động.  
**Sửa**: Chuyển script vào `.github/scripts/emulator_smoke_test.sh`, gọi `bash <file>`.

### Lỗi 6: Watchdog có thể huỷ nhầm buổi ghi mới bắt đầu ngay sau khi dừng buổi trước
**Nguyên nhân**: `scheduleStartWatchdog()` chỉ kiểm tra `RecordingStateHolder.state == RECORDING`, không phân biệt buổi ghi nào — nếu dừng rồi bắt đầu lại trong vòng 5s, watchdog cũ có thể huỷ buổi ghi mới đang chạy tốt.  
**Sửa**: Thêm `recordingSessionId` tăng dần mỗi lần `startRecordingRequest()`, watchdog capture và so khớp session id trước khi hành động.

### Lỗi 7: Race điều kiện khi release AudioCapture/ScreenEncoder lúc dừng ghi
**Nguyên nhân**: `AudioCapture.release()`/`ScreenEncoder.release()` có thể đụng `codec`/`audioRecord` đồng thời với luồng nền (`runLoop()`/MediaCodec callback) vẫn đang thao tác trên cùng object, đặc biệt khi luồng nền bị kẹt (driver OEM lỗi) hoặc chưa kịp thoát.  
**Sửa**: `AudioCapture.awaitFinished()` trả về `Boolean` cho biết luồng có thực sự thoát không; `release()` bỏ qua hoàn toàn nếu luồng còn sống (chấp nhận rò rỉ nhỏ, tránh crash). `ScreenEncoder.release()` đợi `handlerThread.join(500)` sau `quitSafely()` trước khi gọi `codec.stop()/release()`. `cleanupAfterFailure()` cũng chờ `waitForVideoDrain()` trước khi release, giống luồng dừng thành công.

### Lỗi 8: Không xử lý xoay màn hình khi đang ghi
**Nguyên nhân**: `VirtualDisplay` tạo 1 lần với kích thước cố định lúc bắt đầu ghi, không có `DisplayListener`.  
**Sửa**: `ScreenRecordService` đăng ký `DisplayManager.DisplayListener`, gọi `ScreenEncoder.resize()` khi xoay màn hình trong lúc RECORDING/PAUSED. Xem mục "Xoay màn hình khi đang ghi" — mục tiêu chính là không crash, không đảm bảo tỷ lệ khung hình hoàn hảo ở mọi trường hợp.

### Lỗi 9: ULTRA quality có thể crash trên thiết bị không hỗ trợ
**Nguyên nhân**: Không kiểm tra `MediaCodecInfo.CodecCapabilities` trước `codec.configure()`.  
**Sửa**: `isVideoConfigSupported()` (dùng `MediaCodecList.findEncoderForFormat()`) kiểm tra trước khi tạo `ScreenEncoder`, trả lỗi rõ ràng nếu không hỗ trợ.

### Lỗi 10: Mồ côi tài nguyên/file khi OS hoặc OEM (MIUI/Huawei) kill service giữa buổi ghi
**Nguyên nhân**: `onDestroy()` trước đây chỉ gỡ `tickerRunnable`, không release encoder/audio/muxer, không xoá bản ghi MediaStore dở dang (`IS_PENDING=1`).  
**Sửa**: `onDestroy()` giờ cố dọn tài nguyên native + xoá file dở dang tốt nhất có thể nếu state đang RECORDING/PAUSED/STOPPING lúc bị huỷ. Không thể phục hồi nội dung đã ghi trong trường hợp này — chỉ tránh mồ côi tài nguyên/file "đang chờ" vô hình trong Gallery.

### Lỗi 11 (Flutter): Pause/Resume không bắt lỗi, có thể bấm đúp
**Nguyên nhân**: `onPause`/`onResume` gọi thẳng Future không await/try-catch, không có cờ chặn bấm đúp như nút Dừng.  
**Sửa**: `HomeScreen._onPauseResumePressed()` bọc try/catch + cờ `_pauseResumeBusy`, hiện SnackBar khi lỗi.

### Lỗi 12 (Flutter): EventChannel lỗi bị nuốt âm thầm, UI đứng hình
**Nguyên nhân**: `RecorderProvider` lắng nghe `stateStream` với `onError: (Object _) {}` rỗng.  
**Sửa**: `RecorderProvider._onStreamError()` báo lỗi qua `takePendingError()` rồi tự huỷ + subscribe lại stream.

### Lỗi 13 (Flutter): Race huỷ đếm ngược vẫn có thể gọi startRecording()
**Nguyên nhân**: `_startNow()` (lên lịch qua `addPostFrameCallback`) không có cờ đánh dấu đã huỷ, chỉ dựa vào thứ tự FIFO của MethodChannel.  
**Sửa**: Thêm cờ `_cancelled` trong `CountdownScreen`, set khi `_cancel()` chạy, kiểm tra đầu `_startNow()`.

### Lỗi 14 (Flutter): Chọn thư mục lưu (SAF) không bắt lỗi, có thể mở nhiều hộp thoại
**Nguyên nhân**: `_pickFolder()` không try/catch, không có cờ chặn bấm nhiều lần.  
**Sửa**: Tách `_SaveLocationCard` thành `StatefulWidget` riêng với cờ `_picking` + try/catch trong `settings_screen.dart`.

---

## Vấn đề còn tồn tại / Chưa xác nhận

1. **Âm thanh nội bộ không ghi được trên một số máy Samsung/thiết bị OEM**: `AudioRecord` có thể build thành công nhưng không thực sự capture được audio từ `AudioPlaybackCaptureConfiguration`. Đây là vấn đề phần cứng/firmware, **chưa có fix tận gốc**. Đã thêm **heuristic cảnh báo** (`audioLikelySilent`, xem mục "Cảnh báo audio im lặng" ở trên): nếu PCM gần như im lặng suốt 5 giây đầu, người dùng được cảnh báo ngay trong lúc ghi thay vì phát hiện ra video câm sau khi ghi xong. Vẫn cần test trên thiết bị thật (đặc biệt Samsung) để xác nhận heuristic này bắt đúng trường hợp lỗi mà không báo nhầm khi người dùng cố ý ghi màn hình im lặng.

2. **Chưa test đủ các kịch bản trên thiết bị thật** (đã có code fix/giảm nhẹ, nhưng CI/emulator không xác nhận được):
   - Xoay màn hình khi đang ghi — đã thêm `DisplayListener` + `resize()` (Lỗi 8), cần xác nhận không crash và chất lượng video chấp nhận được ở các mức xoay khác nhau
   - Ghi dài >10 phút
   - Các mức chất lượng ULTRA (4K/60fps) — đã thêm kiểm tra `isVideoConfigSupported()` (Lỗi 9), cần xác nhận trên thiết bị thật vừa hỗ trợ vừa không hỗ trợ
   - SAF folder sau khi khởi động lại máy
   - Các thương hiệu kill foreground service (Xiaomi MIUI, Huawei) — đã thêm cleanup trong `onDestroy()` (Lỗi 10), cần xác nhận thực tế trên thiết bị các hãng này

3. **Người dùng báo vẫn còn lỗi** (Samsung Galaxy 12): chưa rõ cụ thể là lỗi gì sau các bản sửa mới nhất — cần APK từ commit mới nhất (sau các fix Lỗi 6-14) để test lại. Nếu vẫn còn lỗi, cảnh báo `audioLikelySilent` mới thêm có thể giúp thu hẹp xem có phải vấn đề audio-im-lặng hay không.

4. **Chưa làm (đã cân nhắc, quyết định không làm ở lần sửa lỗi gần nhất)**: trộn thêm mic vào audio nội bộ (audio source modes như app tham khảo `AppVideo`), danh sách bản ghi trong app (recording history/library screen).

---

## Build & Deploy

```bash
# Build debug APK
flutter build apk --debug
# Output: build/app/outputs/flutter-apk/app-debug.apk

# Build release APK (chưa có keystore — cần tạo)
flutter build apk --release
```

APK debug build qua CI **tự động chỉ khi push lên `main`** (xem mục "Trigger — CHỈ build tự động trên `main`" ở phần CI/CD phía trên). Download từ tab **Actions** → chọn run → **Artifacts** → `kap-record-debug-apk`.

---

## Môi trường phát triển

Container hiện tại (claude-code-remote) **không có** Flutter SDK và Android SDK — bị proxy chặn `dl.google.com`. Toàn bộ build và test chạy qua **GitHub Actions CI**. Để làm việc trực tiếp cần:

- Flutter SDK stable (3.44+)
- Android SDK với build-tools 34+
- Java 17 (temurin)
- Thiết bị Android thật API 29+ (Android 10+) để test âm thanh nội bộ — emulator không đáng tin cậy với `AudioPlaybackCaptureConfiguration`

---

## Phụ thuộc chính (pubspec.yaml)

```yaml
dependencies:
  provider: ^6.1.2          # State management
  shared_preferences: ^2.3.2 # Lưu cài đặt
  permission_handler: ^11.3.1 # Xin quyền runtime
  intl: ^0.19.0              # Định dạng số/ngày tháng
```

**Không dùng** plugin ghi màn hình có sẵn (không có plugin nào hỗ trợ đủ MediaProjection + AudioPlaybackCapture + MediaMuxer + Tile).

---

## String resources quan trọng

File: `android/app/src/main/res/values/strings.xml`

| Key | Giá trị |
|---|---|
| `tile_label` | KapRecord |
| `tile_label_recording` | Đang quay |
| `tile_label_paused` | Tạm dừng |
| `notification_channel_name` | Ghi màn hình |
| `notification_recording_title` | Đang ghi màn hình |
| `notification_paused_title` | Tạm dừng ghi hình |
| `notification_armed_title` | Sẵn sàng ghi hình |
| `action_pause` | Tạm dừng |
| `action_resume` | Tiếp tục |
| `action_stop` | Dừng |
