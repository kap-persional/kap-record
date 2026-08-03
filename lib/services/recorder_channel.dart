import 'package:flutter/services.dart';

import '../models/recording_state.dart';
import '../models/save_location.dart';

/// Bọc MethodChannel/EventChannel trao đổi với ScreenRecordService phía Android.
class RecorderChannel {
  RecorderChannel._();

  static final RecorderChannel instance = RecorderChannel._();

  static const _methodChannel = MethodChannel('com.kap.record/recorder');
  static const _eventChannel = EventChannel('com.kap.record/recorder_events');

  Stream<RecordingSnapshot>? _stateStream;

  Stream<RecordingSnapshot> get stateStream {
    return _stateStream ??= _eventChannel.receiveBroadcastStream().map((event) {
      final map = Map<String, dynamic>.from(event as Map);
      return RecordingSnapshot.fromMap(map);
    });
  }

  Future<bool> requestProjectionConsent() async {
    final result = await _methodChannel.invokeMethod<bool>('requestProjectionConsent');
    return result ?? false;
  }

  Future<void> startRecording({
    required String videoQuality,
    required String audioQuality,
    required String saveMode,
    String? customUri,
  }) {
    return _methodChannel.invokeMethod<void>('startRecording', {
      'videoQuality': videoQuality,
      'audioQuality': audioQuality,
      'saveMode': saveMode,
      'customUri': customUri,
    });
  }

  Future<void> pauseRecording() => _methodChannel.invokeMethod<void>('pauseRecording');

  Future<void> resumeRecording() => _methodChannel.invokeMethod<void>('resumeRecording');

  Future<void> cancelArmed() => _methodChannel.invokeMethod<void>('cancelArmed');

  Future<StopResult?> stopRecording() async {
    final result = await _methodChannel.invokeMethod<Object?>('stopRecording');
    if (result == null) return null;
    return StopResult.fromMap(Map<String, dynamic>.from(result as Map));
  }

  Future<SaveFolderResult?> pickSaveFolder() async {
    final result = await _methodChannel.invokeMethod<Object?>('pickSaveFolder');
    if (result == null) return null;
    final map = Map<String, dynamic>.from(result as Map);
    return SaveFolderResult(uri: map['uri'] as String, displayName: map['displayName'] as String);
  }
}
