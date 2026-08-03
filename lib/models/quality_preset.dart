enum VideoQuality { low, medium, high, ultra }

extension VideoQualityX on VideoQuality {
  String get wireName {
    switch (this) {
      case VideoQuality.low:
        return 'low';
      case VideoQuality.medium:
        return 'medium';
      case VideoQuality.high:
        return 'high';
      case VideoQuality.ultra:
        return 'ultra';
    }
  }

  String get label {
    switch (this) {
      case VideoQuality.low:
        return 'Thấp';
      case VideoQuality.medium:
        return 'Trung bình';
      case VideoQuality.high:
        return 'Cao';
      case VideoQuality.ultra:
        return 'Cực cao';
    }
  }

  String get description {
    switch (this) {
      case VideoQuality.low:
        return '720p · 2.5 Mbps · 24fps';
      case VideoQuality.medium:
        return '1080p · 6 Mbps · 30fps';
      case VideoQuality.high:
        return '1440p · 12 Mbps · 30fps';
      case VideoQuality.ultra:
        return '4K (2160p) · 28 Mbps · 60fps — file rất nặng (~180-200MB/phút)';
    }
  }

  static VideoQuality fromWireName(String? value) {
    return VideoQuality.values.firstWhere(
      (e) => e.wireName == value,
      orElse: () => VideoQuality.medium,
    );
  }
}

enum AudioQuality { low, medium, high, ultra }

extension AudioQualityX on AudioQuality {
  String get wireName {
    switch (this) {
      case AudioQuality.low:
        return 'low';
      case AudioQuality.medium:
        return 'medium';
      case AudioQuality.high:
        return 'high';
      case AudioQuality.ultra:
        return 'ultra';
    }
  }

  String get label {
    switch (this) {
      case AudioQuality.low:
        return 'Thấp';
      case AudioQuality.medium:
        return 'Trung bình';
      case AudioQuality.high:
        return 'Cao';
      case AudioQuality.ultra:
        return 'Cực cao';
    }
  }

  String get description {
    switch (this) {
      case AudioQuality.low:
        return '96 kbps · 44.1kHz';
      case AudioQuality.medium:
        return '128 kbps · 44.1kHz';
      case AudioQuality.high:
        return '192 kbps · 48kHz';
      case AudioQuality.ultra:
        return '256 kbps · 48kHz';
    }
  }

  static AudioQuality fromWireName(String? value) {
    return AudioQuality.values.firstWhere(
      (e) => e.wireName == value,
      orElse: () => AudioQuality.medium,
    );
  }
}
