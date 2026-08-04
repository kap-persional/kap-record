import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
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
  bool _pauseResumeBusy = false;

  Future<void> _onRecordPressed() async {
    final micReady = await _ensureMicrophonePermission();
    if (!micReady || !mounted) return;
    // Quyền Thông báo không bắt buộc để ghi hình (chỉ ảnh hưởng việc hiện thông báo trạng thái
    // khi đang ghi), nên chỉ xin thêm chứ không chặn luồng nếu bị từ chối.
    if (await Permission.notification.status.then((s) => !s.isGranted)) {
      await Permission.notification.request();
    }
    if (!mounted) return;

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

  /// Kiểm tra/xin quyền Micro TRƯỚC khi chạy qua cả luồng xin đồng ý MediaProjection +
  /// đếm ngược — nếu thiếu quyền này thì ghi hình chắc chắn thất bại (bắt buộc theo API
  /// dù app không dùng micro để nghe), nên chặn sớm ngay tại đây thay vì để lỗi hiện ra
  /// sau khi người dùng đã chờ hết cả đếm ngược.
  Future<bool> _ensureMicrophonePermission() async {
    var status = await Permission.microphone.status;
    if (status.isGranted) return true;

    status = await Permission.microphone.request();
    if (status.isGranted) return true;
    if (!mounted) return false;

    if (status.isPermanentlyDenied) {
      await _showMicPermissionDeniedDialog();
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Cần cấp quyền Micro để ghi được âm thanh nội bộ (không dùng để nghe qua micro thật)',
          ),
        ),
      );
    }
    return false;
  }

  Future<void> _showMicPermissionDeniedDialog() {
    return showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cần quyền Micro'),
        content: const Text(
          'KapRecord cần quyền "Micro" theo yêu cầu của hệ thống Android để ghi được âm thanh '
          'nội bộ (nhạc, video đang phát trên máy) — ứng dụng KHÔNG dùng micro thật để nghe.\n\n'
          'Bạn đã từ chối quyền này trước đó nên Android sẽ không tự hiện hộp thoại xin quyền '
          'nữa — hãy vào Cài đặt ứng dụng để cấp quyền thủ công.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Đóng'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(context).pop();
              openAppSettings();
            },
            child: const Text('Mở Cài đặt'),
          ),
        ],
      ),
    );
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

  /// Bọc chung cho Pause/Resume: chặn bấm đúp trong lúc lệnh trước còn đang chạy, và báo
  /// lỗi rõ ràng nếu native ném PlatformException thay vì để rơi mất không ai biết.
  Future<void> _onPauseResumePressed(Future<void> Function() action) async {
    if (_pauseResumeBusy) return;
    setState(() => _pauseResumeBusy = true);
    try {
      await action();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Thao tác thất bại: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _pauseResumeBusy = false);
    }
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
            if (isActive && recorder.audioLikelySilent) ...[
              const SizedBox(height: 16),
              const _SilentAudioBanner(),
            ],
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
                onPause: () => _onPauseResumePressed(() => context.read<RecorderProvider>().pause()),
                onResume: () => _onPauseResumePressed(() => context.read<RecorderProvider>().resume()),
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

class _SilentAudioBanner extends StatelessWidget {
  const _SilentAudioBanner();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: Colors.orange.withValues(alpha: 0.15),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Colors.orange),
        ),
        child: const Row(
          children: [
            Icon(Icons.warning_amber_rounded, color: Colors.orange),
            SizedBox(width: 12),
            Expanded(
              child: Text(
                'Có vẻ chưa phát hiện âm thanh nội bộ nào — kiểm tra ứng dụng đang phát có '
                'âm lượng không. Một số thiết bị có thể không hỗ trợ ghi âm thanh nội bộ.',
                style: TextStyle(fontSize: 13),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
