import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'providers/settings_provider.dart';
import 'screens/countdown_screen.dart';
import 'screens/home_screen.dart';
import 'screens/onboarding_permissions_screen.dart';
import 'screens/settings_screen.dart';

class KapRecordApp extends StatelessWidget {
  const KapRecordApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'KapRecord',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: const Color(0xFFE53935), useMaterial3: true),
      darkTheme: ThemeData(
        colorSchemeSeed: const Color(0xFFE53935),
        brightness: Brightness.dark,
        useMaterial3: true,
      ),
      // Không đặt initialRoute: mặc định MaterialApp lấy route ban đầu từ phía native
      // (MainActivity dùng "/", CountdownActivity override getInitialRoute() thành "/countdown"
      // khi mở từ Quick Settings Tile).
      routes: {
        '/': (context) => const _RootRouter(),
        '/settings': (context) => const SettingsScreen(),
        '/countdown': (context) => const CountdownScreen(),
      },
    );
  }
}

class _RootRouter extends StatelessWidget {
  const _RootRouter();

  @override
  Widget build(BuildContext context) {
    final onboardingDone = context.watch<SettingsProvider>().onboardingDone;
    if (!onboardingDone) {
      return const OnboardingPermissionsScreen();
    }
    return const HomeScreen();
  }
}
