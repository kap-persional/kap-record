import 'package:flutter/foundation.dart';

import '../models/quality_preset.dart';
import '../models/save_location.dart';
import '../services/settings_service.dart';

class SettingsProvider extends ChangeNotifier {
  SettingsProvider(this._service);

  final SettingsService _service;

  VideoQuality get videoQuality => _service.videoQuality;
  AudioQuality get audioQuality => _service.audioQuality;
  int get countdownSeconds => _service.countdownSeconds;
  SaveLocation get saveLocation => _service.saveLocation;
  bool get onboardingDone => _service.onboardingDone;

  Future<void> setVideoQuality(VideoQuality value) async {
    await _service.setVideoQuality(value);
    notifyListeners();
  }

  Future<void> setAudioQuality(AudioQuality value) async {
    await _service.setAudioQuality(value);
    notifyListeners();
  }

  Future<void> setCountdownSeconds(int value) async {
    await _service.setCountdownSeconds(value);
    notifyListeners();
  }

  Future<void> setSaveLocation(SaveLocation location) async {
    await _service.setSaveLocation(location);
    notifyListeners();
  }

  Future<void> resetToGallery() async {
    await _service.resetToGallery();
    notifyListeners();
  }

  Future<void> completeOnboarding() async {
    await _service.setOnboardingDone(true);
    notifyListeners();
  }
}
