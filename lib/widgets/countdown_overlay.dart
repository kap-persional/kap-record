import 'package:flutter/material.dart';

class CountdownOverlay extends StatelessWidget {
  const CountdownOverlay({
    super.key,
    required this.remainingSeconds,
    required this.starting,
    required this.onCancel,
  });

  final int remainingSeconds;
  final bool starting;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (starting) ...[
          const CircularProgressIndicator(color: Colors.white),
          const SizedBox(height: 24),
          const Text(
            'Đang chuẩn bị ghi hình…',
            style: TextStyle(color: Colors.white, fontSize: 18),
          ),
        ] else ...[
          Text(
            remainingSeconds > 0 ? '$remainingSeconds' : '',
            style: const TextStyle(color: Colors.white, fontSize: 96, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 16),
          const Text(
            'Chuẩn bị ghi màn hình…',
            style: TextStyle(color: Colors.white70, fontSize: 16),
          ),
          const SizedBox(height: 32),
          OutlinedButton(
            onPressed: onCancel,
            style: OutlinedButton.styleFrom(
              foregroundColor: Colors.white,
              side: const BorderSide(color: Colors.white54),
            ),
            child: const Text('Huỷ'),
          ),
        ],
      ],
    );
  }
}
