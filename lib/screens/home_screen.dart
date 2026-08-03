import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/quality_preset.dart';
import '../models/recording_state.dart';
import '../providers/recorder_provider.dart';
import '../providers/settings_provider.dart';
import '../widgets/record_button.dart';
import '../widgets/timer_display.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  Future<void> _onRecordPressed(BuildContext context) async {
    final recorder = context.read<RecorderProvider>();
    final granted = await recorder.requestConsent();
    if (!granted) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Bạn cần đồng ý chia sẻ màn hình để bắt đầu ghi hình')),
        );
      }
      return;
    }
    if (context.mounted) {
      await Navigator.of(context).pushNamed('/countdown');
    }
  }

  Future<void> _onStopPressed(BuildContext context) async {
    final recorder = context.read<RecorderProvider>();
    try {
      final result = await recorder.stop();
      if (context.mounted && result != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Đã lưu: ${result.displayPath}')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Lỗi khi dừng ghi hình: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final recorder = context.watch<RecorderProvider>();
    final settings = context.watch<SettingsProvider>();
    final phase = recorder.phase;
    final isActive = phase == RecorderPhase.recording || phase == RecorderPhase.paused;

    return Scaffold(
      appBar: AppBar(
        title: const Text('KapRecord'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'Cài đặt',
            onPressed: () => Navigator.of(context).pushNamed('/settings'),
          ),
        ],
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            TimerDisplay(elapsedSeconds: recorder.elapsedSeconds, isPaused: recorder.isPaused),
            const SizedBox(height: 40),
            RecordButton(
              phase: phase,
              onStart: () => _onRecordPressed(context),
              onPause: () => context.read<RecorderProvider>().pause(),
              onResume: () => context.read<RecorderProvider>().resume(),
              onStop: () => _onStopPressed(context),
            ),
            const SizedBox(height: 32),
            if (!isActive) ...[
              Wrap(
                spacing: 8,
                alignment: WrapAlignment.center,
                children: [
                  Chip(label: Text('Video: ${settings.videoQuality.label}')),
                  Chip(label: Text('Âm thanh: ${settings.audioQuality.label}')),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}
