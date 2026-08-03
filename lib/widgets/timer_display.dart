import 'package:flutter/material.dart';

class TimerDisplay extends StatelessWidget {
  const TimerDisplay({super.key, required this.elapsedSeconds, required this.isPaused});

  final int elapsedSeconds;
  final bool isPaused;

  String _format(int totalSeconds) {
    final h = totalSeconds ~/ 3600;
    final m = (totalSeconds % 3600) ~/ 60;
    final s = totalSeconds % 60;
    final mm = m.toString().padLeft(2, '0');
    final ss = s.toString().padLeft(2, '0');
    if (h > 0) {
      return '${h.toString().padLeft(2, '0')}:$mm:$ss';
    }
    return '$mm:$ss';
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(_format(elapsedSeconds), style: Theme.of(context).textTheme.displayMedium),
        if (isPaused)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              'Đã tạm dừng',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Colors.orange),
            ),
          ),
      ],
    );
  }
}
