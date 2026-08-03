enum SaveLocationMode { gallery, custom }

class SaveLocation {
  const SaveLocation({required this.mode, this.uri, required this.displayName});

  final SaveLocationMode mode;
  final String? uri;
  final String displayName;

  static const defaultGallery = SaveLocation(
    mode: SaveLocationMode.gallery,
    uri: null,
    displayName: 'Movies/KapRecord (Thư viện ảnh máy)',
  );

  String get wireMode => mode == SaveLocationMode.custom ? 'custom' : 'gallery';
}

class SaveFolderResult {
  const SaveFolderResult({required this.uri, required this.displayName});

  final String uri;
  final String displayName;
}
