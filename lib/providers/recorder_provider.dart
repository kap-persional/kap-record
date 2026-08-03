import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/recording_state.dart';
import '../services/recorder_channel.dart';

class RecorderProvider extends ChangeNotifier {
  RecorderProvider(this._channel) {
    _subscription = _channel.stateStream.listen(_onSnapshot, onError: (Object _) {});
  }

  final RecorderChannel _channel;
  StreamSubscription<RecordingSnapshot>? _subscription;

  RecordingSnapshot _snapshot = RecordingSnapshot.idleInitial;

  RecordingSnapshot get snapshot => _snapshot;
  RecorderPhase get phase => _snapshot.phase;
  int get elapsedSeconds => _snapshot.elapsedSeconds;
  bool get isPaused => _snapshot.isPaused;

  void _onSnapshot(RecordingSnapshot snapshot) {
    _snapshot = snapshot;
    notifyListeners();
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
