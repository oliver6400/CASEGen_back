class SpeechInputBuffer {
  String _textBeforeDictation = '';
  bool _receivedSpeech = false;

  void begin(String currentText) {
    _textBeforeDictation = currentText;
    _receivedSpeech = false;
  }

  String applyRecognition(String recognizedText) {
    final normalized = recognizedText.trim();
    if (normalized.isEmpty) return _textBeforeDictation;
    _receivedSpeech = true;
    return normalized;
  }

  bool get receivedSpeech => _receivedSpeech;

  String get textBeforeDictation => _textBeforeDictation;
}
