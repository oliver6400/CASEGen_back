import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:speech_to_text/speech_recognition_error.dart';
import 'package:speech_to_text/speech_recognition_result.dart';
import 'package:speech_to_text/speech_to_text.dart';

enum SpeechServiceStatus { idle, listening, processing, unavailable, error }

enum SpeechFailureKind { permissionDenied, noContent, unavailable, temporary }

class SpeechLocale {
  const SpeechLocale(this.id, this.name);

  final String id;
  final String name;
}

class SpeechUpdate {
  const SpeechUpdate(this.text, {required this.isFinal});

  final String text;
  final bool isFinal;
}

class SpeechEngineFailure {
  const SpeechEngineFailure(this.kind);

  final SpeechFailureKind kind;
}

abstract class SpeechRecognitionEngine {
  Future<bool> initialize({
    required ValueChanged<String> onStatus,
    required ValueChanged<SpeechEngineFailure> onError,
  });

  Future<List<SpeechLocale>> locales();

  Future<String?> systemLocaleId();

  Future<void> listen({
    required ValueChanged<SpeechUpdate> onResult,
    required String? localeId,
  });

  Future<void> stop();

  Future<void> cancel();
}

class SpeechToTextEngine implements SpeechRecognitionEngine {
  SpeechToTextEngine({SpeechToText? speechToText})
    : _speechToText = speechToText ?? SpeechToText();

  final SpeechToText _speechToText;

  @override
  Future<bool> initialize({
    required ValueChanged<String> onStatus,
    required ValueChanged<SpeechEngineFailure> onError,
  }) => _speechToText.initialize(
    onStatus: onStatus,
    onError: (error) => onError(_mapError(error)),
  );

  @override
  Future<List<SpeechLocale>> locales() async => (await _speechToText.locales())
      .map((locale) => SpeechLocale(locale.localeId, locale.name))
      .toList(growable: false);

  @override
  Future<String?> systemLocaleId() async =>
      (await _speechToText.systemLocale())?.localeId;

  @override
  Future<void> listen({
    required ValueChanged<SpeechUpdate> onResult,
    required String? localeId,
  }) async {
    await _speechToText.listen(
      onResult: (result) => onResult(_mapResult(result)),
      listenOptions: SpeechListenOptions(
        localeId: localeId,
        partialResults: true,
        cancelOnError: true,
        listenMode: ListenMode.dictation,
        pauseFor: const Duration(seconds: 4),
        listenFor: const Duration(minutes: 1),
      ),
    );
  }

  @override
  Future<void> stop() => _speechToText.stop();

  @override
  Future<void> cancel() => _speechToText.cancel();

  SpeechUpdate _mapResult(SpeechRecognitionResult result) =>
      SpeechUpdate(result.recognizedWords, isFinal: result.finalResult);

  SpeechEngineFailure _mapError(SpeechRecognitionError error) {
    final code = error.errorMsg.toLowerCase();
    if (code.contains('permission')) {
      return const SpeechEngineFailure(SpeechFailureKind.permissionDenied);
    }
    if (code.contains('no_match') || code.contains('speech_timeout')) {
      return const SpeechEngineFailure(SpeechFailureKind.noContent);
    }
    if (code.contains('language_not_supported') ||
        code.contains('language_unavailable')) {
      return const SpeechEngineFailure(SpeechFailureKind.unavailable);
    }
    return const SpeechEngineFailure(SpeechFailureKind.temporary);
  }
}

String? resolvePreferredSpanishLocale(
  List<SpeechLocale> locales,
  String? systemLocaleId,
) {
  String normalize(String value) => value.replaceAll('-', '_').toLowerCase();

  for (final locale in locales) {
    if (normalize(locale.id) == 'es_bo') return locale.id;
  }
  for (final locale in locales) {
    if (normalize(locale.id).startsWith('es_') ||
        normalize(locale.id) == 'es') {
      return locale.id;
    }
  }
  if (systemLocaleId == null || systemLocaleId.trim().isEmpty) return null;
  final normalizedSystem = normalize(systemLocaleId);
  for (final locale in locales) {
    if (normalize(locale.id) == normalizedSystem) return locale.id;
  }
  return systemLocaleId;
}

class SpeechService extends ChangeNotifier {
  SpeechService({SpeechRecognitionEngine? engine})
    : _engine = engine ?? SpeechToTextEngine();

  final SpeechRecognitionEngine _engine;
  SpeechServiceStatus _status = SpeechServiceStatus.idle;
  bool _initialized = false;
  bool _sessionActive = false;
  String _recognizedText = '';
  bool _isFinalResult = false;
  String? _selectedLocaleId;
  String? _message;
  Timer? _finalResultFallback;

  SpeechServiceStatus get status => _status;
  String get recognizedText => _recognizedText;
  bool get isFinalResult => _isFinalResult;
  String? get selectedLocaleId => _selectedLocaleId;
  String? get message => _message;
  bool get isListening => _status == SpeechServiceStatus.listening;

  Future<bool> initialize({bool retry = false}) async {
    if (_initialized && !retry) return true;
    _message = null;
    try {
      final available = await _engine.initialize(
        onStatus: _handleEngineStatus,
        onError: _handleEngineError,
      );
      if (!available) {
        _initialized = false;
        if (_message == null) {
          _setFailure(
            SpeechServiceStatus.unavailable,
            'El reconocimiento de voz no está disponible en este dispositivo.',
          );
        }
        return false;
      }
      final locales = await _engine.locales();
      final systemLocale = await _engine.systemLocaleId();
      _selectedLocaleId = resolvePreferredSpanishLocale(locales, systemLocale);
      _initialized = true;
      _status = SpeechServiceStatus.idle;
      notifyListeners();
      return true;
    } on Object {
      _initialized = false;
      _setFailure(
        SpeechServiceStatus.error,
        'No fue posible utilizar el reconocimiento de voz.',
      );
      return false;
    }
  }

  Future<bool> startListening() async {
    if (_status == SpeechServiceStatus.processing) return false;
    final available = await initialize(
      retry:
          _status == SpeechServiceStatus.unavailable ||
          _status == SpeechServiceStatus.error,
    );
    if (!available) return false;

    _finalResultFallback?.cancel();
    _recognizedText = '';
    _isFinalResult = false;
    _message = null;
    _sessionActive = true;
    _status = SpeechServiceStatus.listening;
    notifyListeners();
    try {
      await _engine.listen(
        localeId: _selectedLocaleId,
        onResult: _handleResult,
      );
      return true;
    } on Object {
      _sessionActive = false;
      _setFailure(
        SpeechServiceStatus.error,
        'No fue posible utilizar el reconocimiento de voz.',
      );
      return false;
    }
  }

  Future<void> stopListening() async {
    if (!_sessionActive) return;
    _status = SpeechServiceStatus.processing;
    notifyListeners();
    try {
      await _engine.stop();
    } on Object {
      _sessionActive = false;
      _setFailure(
        SpeechServiceStatus.error,
        'No fue posible utilizar el reconocimiento de voz.',
      );
    }
  }

  Future<void> cancelListening() async {
    _finalResultFallback?.cancel();
    if (_sessionActive) {
      try {
        await _engine.cancel();
      } on Object {
        // Cancellation is best effort during navigation/background changes.
      }
    }
    _sessionActive = false;
    _status = SpeechServiceStatus.idle;
    notifyListeners();
  }

  void clearMessage() => _message = null;

  void _handleResult(SpeechUpdate update) {
    if (!_sessionActive) return;
    _recognizedText = update.text.trim();
    _isFinalResult = update.isFinal;
    if (update.isFinal) {
      _status = SpeechServiceStatus.processing;
      _finalResultFallback?.cancel();
      _finalResultFallback = Timer(const Duration(seconds: 1), _finishSession);
    }
    notifyListeners();
  }

  void _handleEngineStatus(String engineStatus) {
    if (!_sessionActive) return;
    final normalized = engineStatus.toLowerCase();
    if (normalized == 'listening') {
      _status = SpeechServiceStatus.listening;
      notifyListeners();
      return;
    }
    if (normalized == 'done' || normalized == 'notlistening') {
      _finishSession();
    }
  }

  void _finishSession() {
    if (!_sessionActive) return;
    _finalResultFallback?.cancel();
    _sessionActive = false;
    if (_recognizedText.isEmpty) {
      _setFailure(
        SpeechServiceStatus.error,
        'No se pudo reconocer la instrucción. Intenta nuevamente.',
      );
      return;
    }
    _status = SpeechServiceStatus.idle;
    notifyListeners();
  }

  void _handleEngineError(SpeechEngineFailure failure) {
    _sessionActive = false;
    final message = switch (failure.kind) {
      SpeechFailureKind.permissionDenied =>
        'Se necesita permiso para usar el micrófono.',
      SpeechFailureKind.noContent =>
        'No se pudo reconocer la instrucción. Intenta nuevamente.',
      SpeechFailureKind.unavailable =>
        'El reconocimiento de voz no está disponible en este dispositivo.',
      SpeechFailureKind.temporary =>
        'No fue posible utilizar el reconocimiento de voz.',
    };
    final state = failure.kind == SpeechFailureKind.unavailable
        ? SpeechServiceStatus.unavailable
        : SpeechServiceStatus.error;
    _setFailure(state, message);
  }

  void _setFailure(SpeechServiceStatus status, String message) {
    _finalResultFallback?.cancel();
    _sessionActive = false;
    _status = status;
    _message = message;
    notifyListeners();
  }

  @override
  void dispose() {
    _finalResultFallback?.cancel();
    if (_sessionActive) {
      _sessionActive = false;
      unawaited(_engine.cancel().catchError((Object _) {}));
    }
    super.dispose();
  }
}
