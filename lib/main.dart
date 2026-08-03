import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'app.dart';
import 'providers/recorder_provider.dart';
import 'providers/settings_provider.dart';
import 'services/recorder_channel.dart';
import 'services/settings_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final settingsService = await SettingsService.create();
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => SettingsProvider(settingsService)),
        ChangeNotifierProvider(create: (_) => RecorderProvider(RecorderChannel.instance)),
      ],
      child: const KapRecordApp(),
    ),
  );
}
