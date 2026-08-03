import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';

import '../providers/settings_provider.dart';

class OnboardingPermissionsScreen extends StatefulWidget {
  const OnboardingPermissionsScreen({super.key});

  @override
  State<OnboardingPermissionsScreen> createState() => _OnboardingPermissionsScreenState();
}

class _OnboardingPermissionsScreenState extends State<OnboardingPermissionsScreen> {
  bool _requesting = false;

  Future<void> _continue() async {
    setState(() => _requesting = true);
    await [Permission.microphone, Permission.notification].request();
    if (!mounted) return;
    await context.read<SettingsProvider>().completeOnboarding();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Icon(Icons.screen_search_desktop, size: 72, color: Colors.redAccent),
              const SizedBox(height: 24),
              Text(
                'Chào mừng đến với KapRecord',
                style: Theme.of(context).textTheme.headlineSmall,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              const Text(
                'Ứng dụng ghi lại màn hình kèm âm thanh PHÁT RA TỪ CHÍNH MÁY (nhạc, video…), '
                'KHÔNG ghi âm micro và không bị ảnh hưởng bởi tiếng ồn xung quanh.\n\n'
                'Android yêu cầu quyền "Micro" theo quy định hệ thống để có thể ghi được âm thanh '
                'nội bộ này, dù ứng dụng không thực sự dùng đến micro.',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 32),
              FilledButton(
                onPressed: _requesting ? null : _continue,
                child: _requesting
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Tiếp tục'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
