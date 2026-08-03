import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';

import '../models/quality_preset.dart';
import '../models/save_location.dart';
import '../providers/settings_provider.dart';
import '../services/recorder_channel.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  Future<void> _pickFolder(BuildContext context) async {
    final result = await RecorderChannel.instance.pickSaveFolder();
    if (result == null || !context.mounted) return;
    await context.read<SettingsProvider>().setSaveLocation(
          SaveLocation(mode: SaveLocationMode.custom, uri: result.uri, displayName: result.displayName),
        );
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsProvider>();

    return Scaffold(
      appBar: AppBar(title: const Text('Cài đặt')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const _SectionTitle('Chất lượng video'),
          _QualityPicker<VideoQuality>(
            values: VideoQuality.values,
            selected: settings.videoQuality,
            labelOf: (v) => v.label,
            descriptionOf: (v) => v.description,
            onSelected: (v) => context.read<SettingsProvider>().setVideoQuality(v),
          ),
          const SizedBox(height: 24),
          const _SectionTitle('Chất lượng âm thanh'),
          _QualityPicker<AudioQuality>(
            values: AudioQuality.values,
            selected: settings.audioQuality,
            labelOf: (v) => v.label,
            descriptionOf: (v) => v.description,
            onSelected: (v) => context.read<SettingsProvider>().setAudioQuality(v),
          ),
          const SizedBox(height: 24),
          const _SectionTitle('Đếm ngược trước khi quay'),
          _CountdownPicker(
            value: settings.countdownSeconds,
            onChanged: (v) => context.read<SettingsProvider>().setCountdownSeconds(v),
          ),
          const SizedBox(height: 24),
          const _SectionTitle('Nơi lưu video'),
          Card(
            child: ListTile(
              leading: const Icon(Icons.folder_outlined),
              title: Text(settings.saveLocation.displayName),
              subtitle: const Text('Bấm để đổi thư mục lưu'),
              trailing: settings.saveLocation.mode == SaveLocationMode.custom
                  ? IconButton(
                      icon: const Icon(Icons.restore),
                      tooltip: 'Về mặc định (Thư viện ảnh)',
                      onPressed: () => context.read<SettingsProvider>().resetToGallery(),
                    )
                  : null,
              onTap: () => _pickFolder(context),
            ),
          ),
          const SizedBox(height: 24),
          const _SectionTitle('Quyền ứng dụng'),
          const _PermissionRow(
            permission: Permission.microphone,
            label: 'Ghi âm thanh nội bộ',
            description: 'Bắt buộc theo hệ thống Android dù KHÔNG dùng micro',
          ),
          const _PermissionRow(
            permission: Permission.notification,
            label: 'Thông báo',
            description: 'Hiển thị trạng thái đang ghi và nút Tạm dừng/Dừng',
          ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(text, style: Theme.of(context).textTheme.titleMedium),
    );
  }
}

class _QualityPicker<T> extends StatelessWidget {
  const _QualityPicker({
    required this.values,
    required this.selected,
    required this.labelOf,
    required this.descriptionOf,
    required this.onSelected,
  });

  final List<T> values;
  final T selected;
  final String Function(T) labelOf;
  final String Function(T) descriptionOf;
  final ValueChanged<T> onSelected;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: RadioGroup<T>(
        groupValue: selected,
        onChanged: (value) {
          if (value != null) onSelected(value);
        },
        child: Column(
          children: values
              .map(
                (v) => RadioListTile<T>(
                  value: v,
                  title: Text(labelOf(v)),
                  subtitle: Text(descriptionOf(v)),
                ),
              )
              .toList(),
        ),
      ),
    );
  }
}

class _CountdownPicker extends StatelessWidget {
  const _CountdownPicker({required this.value, required this.onChanged});

  final int value;
  final ValueChanged<int> onChanged;

  static const _options = [0, 3, 5, 10];

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      children: _options.map((seconds) {
        final selected = value == seconds;
        return ChoiceChip(
          label: Text(seconds == 0 ? 'Tắt' : '$seconds giây'),
          selected: selected,
          onSelected: (_) => onChanged(seconds),
        );
      }).toList(),
    );
  }
}

class _PermissionRow extends StatefulWidget {
  const _PermissionRow({required this.permission, required this.label, required this.description});

  final Permission permission;
  final String label;
  final String description;

  @override
  State<_PermissionRow> createState() => _PermissionRowState();
}

class _PermissionRowState extends State<_PermissionRow> with WidgetsBindingObserver {
  PermissionStatus? _status;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final status = await widget.permission.status;
    if (mounted) setState(() => _status = status);
  }

  Future<void> _request() async {
    final status = await widget.permission.request();
    if (mounted) setState(() => _status = status);
    if (status.isPermanentlyDenied) {
      await openAppSettings();
    }
  }

  @override
  Widget build(BuildContext context) {
    final granted = _status?.isGranted ?? false;
    return Card(
      child: ListTile(
        leading: Icon(
          granted ? Icons.check_circle : Icons.error_outline,
          color: granted ? Colors.green : Colors.orange,
        ),
        title: Text(widget.label),
        subtitle: Text(widget.description),
        trailing: granted ? null : TextButton(onPressed: _request, child: const Text('Cấp quyền')),
      ),
    );
  }
}
