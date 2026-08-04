import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/recording_state.dart';
import '../services/recorder_channel.dart';

class RecorderProvider extends ChangeNotifier {
  RecorderProvider(this._channel) {
    _subscribe();
  }

  final RecorderChannel _channel;
  StreamSubscription<RecordingSnapshot>? _subscription;

  RecordingSnapshot _snapshot = RecordingSnapshot.idleInitial;
  String? _pendingErrorEvent;
  bool _streamBroken = false;

  RecordingSnapshot get snapshot => _snapshot;
  RecorderPhase get phase => _snapshot.phase;
  int get elapsedSeconds => _snapshot.elapsedSeconds;
  bool get isPaused => _snapshot.isPaused;
  bool get audioLikelySilent => _snapshot.audioLikelySilent;

  void _subscribe() {
    _subscription = _channel.stateStream.listen(_onSnapshot, onError: _onStreamError);
  }

  void _onSnapshot(RecordingSnapshot snapshot) {
    final previousError = _snapshot.error;
    _snapshot = snapshot;
    _streamBroken = false;
    // Chỉ coi là "lỗi mới" khi nó vừa xuất hiện (không có ở snapshot trước) — tránh hiện lại
    // liên tục cùng một lỗi mỗi lần có state khác được đẩy tới.
    if (snapshot.error != null && snapshot.error != previousError) {
      _pendingErrorEvent = snapshot.error;
    }
    notifyListeners();
  }

  /// Luồng EventChannel bị lỗi (ví dụ platform channel gặp sự cố) — không để UI đứng hình
  /// mãi mãi với state cũ mà không có cách nào biết: báo lỗi một lần rồi tự subscribe lại.
  void _onStreamError(Object error) {
    _streamBroken = true;
    _pendingErrorEvent = 'Mất kết nối cập nhật trạng thái ghi hình — đang thử kết nối lại…';
    notifyListeners();
    _subscription?.cancel();
    _subscribe();
  }

  bool get streamBroken => _streamBroken;

  /// Lấy lỗi mới nhất chưa được hiển thị (nếu có) rồi xoá đi, để UI chỉ hiện một lần.
  String? takePendingError() {
    final error = _pendingErrorEvent;
    _pendingErrorEvent = null;
    return error;
  }

  Future<bool> requestConsent() => _channel.requestProjectionConsent();

  Future<void> startRecording({
    required String videoQuality,
    required String audioQuality,
    required String saveMode,
    String? customUri,
  }) {
    return _channel.startRecording(
      videoQuality: videoQuality,
      audioQuality: audioQuality,
      saveMode: saveMode,
      customUri: customUri,
    );
  }

  Future<void> pause() => _channel.pauseRecording();

  Future<void> resume() => _channel.resumeRecording();

  Future<void> cancelArmed() => _channel.cancelArmed();

  Future<StopResult?> stop() => _channel.stopRecording();

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }
}
