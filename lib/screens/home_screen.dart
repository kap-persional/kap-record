import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/quality_preset.dart';
import '../models/recording_state.dart';
import '../providers/recorder_provider.dart';
import '../providers/settings_provider.dart';
import '../widgets/record_button.dart';
import '../widgets/timer_display.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _stopping = false;

  Future<void> _onRecordPressed() async {
    final recorder = context.read<RecorderProvider>();
    final granted = await recorder.requestConsent();
    if (!granted) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Bạn cần đồng ý chia sẻ màn hình để bắt đầu ghi hình')),
        );
      }
      return;
    }
    if (mounted) {
      await Navigator.of(context).pushNamed('/countdown');
    }
  }

  Future<void> _onStopPressed() async {
    if (_stopping) return;
    setState(() => _stopping = true);
    final recorder = context.read<RecorderProvider>();
    try {
      final result = await recorder.stop();
      if (!mounted) return;
      if (result != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Đã lưu: ${result.displayPath}')),
        );
      } else {
        await _showStopFailedDialog('Ghi hình không lưu được. Hãy thử lại.');
      }
    } catch (e) {
      if (mounted) {
        await _showStopFailedDialog('$e');
      }
    } finally {
      if (mounted) setState(() => _stopping = false);
    }
  }

  Future<void> _showStopFailedDialog(String message) {
    return showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Không thể dừng ghi hình'),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Đóng'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(context).pop();
              _onStopPressed();
            },
            child: const Text('Thử lại'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final recorder = context.watch<RecorderProvider>();
    final settings = context.watch<SettingsProvider>();
    final phase = recorder.phase;
    final isActive = phase == RecorderPhase.recording || phase == RecorderPhase.paused;

    final pendingError = recorder.takePendingError();
    if (pendingError != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ghi hình gặp lỗi: $pendingError'),
            duration: const Duration(seconds: 6),
          ),
        );
      });
    }

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
            if (_stopping)
              const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CircularProgressIndicator(),
                  SizedBox(height: 12),
                  Text('Đang dừng ghi hình…'),
                ],
              )
            else
              RecordButton(
                phase: phase,
                onStart: _onRecordPressed,
                onPause: () => context.read<RecorderProvider>().pause(),
                onResume: () => context.read<RecorderProvider>().resume(),
                onStop: _onStopPressed,
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
