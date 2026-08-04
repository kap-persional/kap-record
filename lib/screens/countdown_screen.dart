import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../models/quality_preset.dart';
import '../providers/recorder_provider.dart';
import '../providers/settings_provider.dart';
import '../widgets/countdown_overlay.dart';

/// Màn hình đếm ngược trước khi bắt đầu ghi thật sự. Dùng chung cho cả 2 luồng:
/// mở từ trong app (được push chồng lên Home, Navigator có thể pop về) và mở từ
/// Quick Settings Tile (là route gốc duy nhất của một FlutterEngine/Activity riêng,
/// lúc đó không pop được nên đóng thẳng Activity bằng SystemNavigator.pop()).
class CountdownScreen extends StatefulWidget {
  const CountdownScreen({super.key});

  @override
  State<CountdownScreen> createState() => _CountdownScreenState();
}

class _CountdownScreenState extends State<CountdownScreen> {
  Timer? _timer;
  late int _remaining;
  bool _starting = false;
  bool _cancelled = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _remaining = context.read<SettingsProvider>().countdownSeconds;
    if (_remaining <= 0) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _startNow());
    } else {
      _timer = Timer.periodic(const Duration(seconds: 1), _onTick);
    }
  }

  void _onTick(Timer timer) {
    setState(() => _remaining -= 1);
    if (_remaining <= 0) {
      timer.cancel();
      _startNow();
    }
  }

  Future<void> _startNow() async {
    // Chặn khả năng lệnh startRecording() vẫn được gọi sau khi người dùng đã bấm Huỷ
    // (ví dụ nếu addPostFrameCallback đã lên lịch đúng lúc _cancel() chạy) — không chỉ
    // dựa vào thứ tự FIFO của MethodChannel để đảm bảo điều này.
    if (_starting || _cancelled || !mounted) return;
    setState(() => _starting = true);
    final settings = context.read<SettingsProvider>();
    final recorder = context.read<RecorderProvider>();
    try {
      await recorder.startRecording(
        videoQuality: settings.videoQuality.wireName,
        audioQuality: settings.audioQuality.wireName,
        saveMode: settings.saveLocation.wireMode,
        customUri: settings.saveLocation.uri,
      );
      _finish();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _starting = false;
        _error = 'Không thể bắt đầu ghi hình: $e';
      });
    }
  }

  void _finish() {
    if (!mounted) return;
    if (Navigator.of(context).canPop()) {
      Navigator.of(context).pop();
    } else {
      SystemNavigator.pop();
    }
  }

  Future<void> _cancel() async {
    _cancelled = true;
    _timer?.cancel();
    await context.read<RecorderProvider>().cancelArmed();
    _finish();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Center(
          child: _error != null
              ? _ErrorContent(message: _error!, onClose: _finish)
              : CountdownOverlay(
                  remainingSeconds: _remaining,
                  starting: _starting,
                  onCancel: _cancel,
                ),
        ),
      ),
    );
  }
}

class _ErrorContent extends StatelessWidget {
  const _ErrorContent({required this.message, required this.onClose});

  final String message;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, color: Colors.redAccent, size: 48),
          const SizedBox(height: 16),
          Text(message, style: const TextStyle(color: Colors.white), textAlign: TextAlign.center),
          const SizedBox(height: 24),
          FilledButton(onPressed: onClose, child: const Text('Đóng')),
        ],
      ),
    );
  }
}
