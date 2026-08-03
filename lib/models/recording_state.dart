enum RecorderPhase { idle, armed, recording, paused, stopping }

RecorderPhase _phaseFromWire(String value) {
  switch (value) {
    case 'armed':
      return RecorderPhase.armed;
    case 'recording':
      return RecorderPhase.recording;
    case 'paused':
      return RecorderPhase.paused;
    case 'stopping':
      return RecorderPhase.stopping;
    default:
      return RecorderPhase.idle;
  }
}

class RecordingSnapshot {
  const RecordingSnapshot({
    required this.phase,
    required this.elapsedSeconds,
    required this.isPaused,
    this.error,
  });

  final RecorderPhase phase;
  final int elapsedSeconds;
  final bool isPaused;
  final String? error;

  static const idleInitial = RecordingSnapshot(
    phase: RecorderPhase.idle,
    elapsedSeconds: 0,
    isPaused: false,
  );

  factory RecordingSnapshot.fromMap(Map<String, dynamic> map) {
    return RecordingSnapshot(
      phase: _phaseFromWire(map['state'] as String? ?? 'idle'),
      elapsedSeconds: (map['elapsedSeconds'] as num?)?.toInt() ?? 0,
      isPaused: map['isPaused'] as bool? ?? false,
      error: map['error'] as String?,
    );
  }
}

class StopResult {
  const StopResult({
    required this.uri,
    required this.displayPath,
    required this.durationMs,
    required this.sizeBytes,
  });

  final String uri;
  final String displayPath;
  final int durationMs;
  final int sizeBytes;

  factory StopResult.fromMap(Map<String, dynamic> map) {
    return StopResult(
      uri: map['uri'] as String,
      displayPath: map['displayPath'] as String,
      durationMs: (map['durationMs'] as num).toInt(),
      sizeBytes: (map['sizeBytes'] as num).toInt(),
    );
  }
}
