import 'package:shared_preferences/shared_preferences.dart';

import '../models/quality_preset.dart';
import '../models/save_location.dart';

class SettingsService {
  SettingsService(this._prefs);

  final SharedPreferences _prefs;

  static Future<SettingsService> create() async {
    final prefs = await SharedPreferences.getInstance();
    return SettingsService(prefs);
  }

  static const _keyVideoQuality = 'video_quality';
  static const _keyAudioQuality = 'audio_quality';
  static const _keyCountdownSeconds = 'countdown_seconds';
  static const _keySaveMode = 'save_mode';
  static const _keySaveUri = 'save_uri';
  static const _keySaveDisplayName = 'save_display_name';
  static const _keyOnboardingDone = 'onboarding_done';

  VideoQuality get videoQuality => VideoQualityX.fromWireName(_prefs.getString(_keyVideoQuality));

  Future<void> setVideoQuality(VideoQuality value) => _prefs.setString(_keyVideoQuality, value.wireName);

  AudioQuality get audioQuality => AudioQualityX.fromWireName(_prefs.getString(_keyAudioQuality));

  Future<void> setAudioQuality(AudioQuality value) => _prefs.setString(_keyAudioQuality, value.wireName);

  /// 0 nghĩa là tắt đếm ngược.
  int get countdownSeconds => _prefs.getInt(_keyCountdownSeconds) ?? 3;

  Future<void> setCountdownSeconds(int value) => _prefs.setInt(_keyCountdownSeconds, value);

  SaveLocation get saveLocation {
    final mode = _prefs.getString(_keySaveMode);
    if (mode == 'custom') {
      final uri = _prefs.getString(_keySaveUri);
      final displayName = _prefs.getString(_keySaveDisplayName);
      if (uri != null && displayName != null) {
        return SaveLocation(mode: SaveLocationMode.custom, uri: uri, displayName: displayName);
      }
    }
    return SaveLocation.defaultGallery;
  }

  Future<void> setSaveLocation(SaveLocation location) async {
    await _prefs.setString(_keySaveMode, location.wireMode);
    if (location.uri != null) {
      await _prefs.setString(_keySaveUri, location.uri!);
    } else {
      await _prefs.remove(_keySaveUri);
    }
    await _prefs.setString(_keySaveDisplayName, location.displayName);
  }

  Future<void> resetToGallery() async {
    await _prefs.setString(_keySaveMode, 'gallery');
    await _prefs.remove(_keySaveUri);
    await _prefs.setString(_keySaveDisplayName, SaveLocation.defaultGallery.displayName);
  }

  bool get onboardingDone => _prefs.getBool(_keyOnboardingDone) ?? false;

  Future<void> setOnboardingDone(bool value) => _prefs.setBool(_keyOnboardingDone, value);
}
