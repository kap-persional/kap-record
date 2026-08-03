import 'package:flutter/material.dart';

import '../models/recording_state.dart';

class RecordButton extends StatelessWidget {
  const RecordButton({
    super.key,
    required this.phase,
    required this.onStart,
    required this.onPause,
    required this.onResume,
    required this.onStop,
  });

  final RecorderPhase phase;
  final VoidCallback onStart;
  final VoidCallback onPause;
  final VoidCallback onResume;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) {
    switch (phase) {
      case RecorderPhase.idle:
        return _BigButton(
          icon: Icons.fiber_manual_record,
          label: 'Quay màn hình',
          color: Colors.red,
          onPressed: onStart,
        );
      case RecorderPhase.armed:
      case RecorderPhase.stopping:
        return const _BigButton(
          icon: Icons.hourglass_top,
          label: 'Đang xử lý…',
          color: Colors.grey,
          onPressed: null,
        );
      case RecorderPhase.recording:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _BigButton(icon: Icons.pause, label: 'Tạm dừng', color: Colors.orange, onPressed: onPause),
            const SizedBox(width: 24),
            _BigButton(icon: Icons.stop, label: 'Dừng', color: Colors.red, onPressed: onStop),
          ],
        );
      case RecorderPhase.paused:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _BigButton(icon: Icons.play_arrow, label: 'Tiếp tục', color: Colors.green, onPressed: onResume),
            const SizedBox(width: 24),
            _BigButton(icon: Icons.stop, label: 'Dừng', color: Colors.red, onPressed: onStop),
          ],
        );
    }
  }
}

class _BigButton extends StatelessWidget {
  const _BigButton({
    required this.icon,
    required this.label,
    required this.color,
    required this.onPressed,
  });

  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton.filled(
          iconSize: 40,
          padding: const EdgeInsets.all(24),
          style: IconButton.styleFrom(backgroundColor: color, foregroundColor: Colors.white),
          onPressed: onPressed,
          icon: Icon(icon),
        ),
        const SizedBox(height: 8),
        Text(label),
      ],
    );
  }
}
