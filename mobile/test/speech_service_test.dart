import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/assistant/services/speech_input_buffer.dart';
import 'package:mobile/features/assistant/services/speech_service.dart';

void main() {
  group('resolvePreferredSpanishLocale', () {
    test('prefers Bolivian Spanish when it is available', () {
      final selected = resolvePreferredSpanishLocale(const [
        SpeechLocale('en_US', 'English'),
        SpeechLocale('es_ES', 'Español'),
        SpeechLocale('es_BO', 'Español (Bolivia)'),
      ], 'en_US');

      expect(selected, 'es_BO');
    });

    test('uses another Spanish locale before the system locale', () {
      final selected = resolvePreferredSpanishLocale(const [
        SpeechLocale('en_US', 'English'),
        SpeechLocale('es-MX', 'Español (México)'),
      ], 'en_US');

      expect(selected, 'es-MX');
    });

    test('falls back to the device locale', () {
      final selected = resolvePreferredSpanishLocale(const [
        SpeechLocale('en_US', 'English'),
      ], 'en_US');

      expect(selected, 'en_US');
    });
  });

  group('SpeechService', () {
    test(
      'reports unavailable when the recognition engine is unavailable',
      () async {
        final service = SpeechService(
          engine: FakeSpeechEngine(available: false),
        );
        addTearDown(service.dispose);

        expect(await service.initialize(), isFalse);
        expect(service.status, SpeechServiceStatus.unavailable);
        expect(
          service.message,
          'El reconocimiento de voz no está disponible en este dispositivo.',
        );
      },
    );

    test('moves through listening, processing and idle states', () async {
      final engine = FakeSpeechEngine(
        availableLocales: const [SpeechLocale('es_BO', 'Español (Bolivia)')],
      );
      final service = SpeechService(engine: engine);
      addTearDown(service.dispose);

      expect(await service.startListening(), isTrue);
      expect(service.status, SpeechServiceStatus.listening);
      expect(service.selectedLocaleId, 'es_BO');

      engine.emitResult('crea una clase', isFinal: false);
      expect(service.recognizedText, 'crea una clase');
      expect(service.status, SpeechServiceStatus.listening);

      await service.stopListening();
      expect(service.status, SpeechServiceStatus.processing);

      engine.emitResult('crea una clase Cliente', isFinal: true);
      expect(service.status, SpeechServiceStatus.processing);
      engine.emitStatus('done');

      expect(service.status, SpeechServiceStatus.idle);
      expect(service.recognizedText, 'crea una clase Cliente');
      expect(service.isFinalResult, isTrue);
    });

    test('maps microphone permission errors to a friendly message', () async {
      final engine = FakeSpeechEngine();
      final service = SpeechService(engine: engine);
      addTearDown(service.dispose);
      await service.startListening();

      engine.emitError(SpeechFailureKind.permissionDenied);

      expect(service.status, SpeechServiceStatus.error);
      expect(service.message, 'Se necesita permiso para usar el micrófono.');
    });

    test(
      'preserves a permission error reported during initialization',
      () async {
        final service = SpeechService(
          engine: FakeSpeechEngine(
            available: false,
            initializationError: SpeechFailureKind.permissionDenied,
          ),
        );
        addTearDown(service.dispose);

        expect(await service.initialize(), isFalse);
        expect(service.status, SpeechServiceStatus.error);
        expect(service.message, 'Se necesita permiso para usar el micrófono.');
      },
    );
  });

  test('final speech text replaces input only after recognition arrives', () {
    final buffer = SpeechInputBuffer()..begin('texto que no debe perderse');

    expect(buffer.textBeforeDictation, 'texto que no debe perderse');
    expect(buffer.receivedSpeech, isFalse);
    expect(
      buffer.applyRecognition('crea una clase Cliente'),
      'crea una clase Cliente',
    );
    expect(buffer.receivedSpeech, isTrue);
  });
}

class FakeSpeechEngine implements SpeechRecognitionEngine {
  FakeSpeechEngine({
    this.available = true,
    this.availableLocales = const [SpeechLocale('en_US', 'English')],
    this.deviceLocale = 'en_US',
    this.initializationError,
  });

  final bool available;
  final List<SpeechLocale> availableLocales;
  final String? deviceLocale;
  final SpeechFailureKind? initializationError;
  void Function(String)? _statusListener;
  void Function(SpeechEngineFailure)? _errorListener;
  void Function(SpeechUpdate)? _resultListener;

  @override
  Future<bool> initialize({
    required void Function(String) onStatus,
    required void Function(SpeechEngineFailure) onError,
  }) async {
    _statusListener = onStatus;
    _errorListener = onError;
    if (initializationError != null) {
      onError(SpeechEngineFailure(initializationError!));
    }
    return available;
  }

  @override
  Future<List<SpeechLocale>> locales() async => availableLocales;

  @override
  Future<String?> systemLocaleId() async => deviceLocale;

  @override
  Future<void> listen({
    required void Function(SpeechUpdate) onResult,
    required String? localeId,
  }) async {
    _resultListener = onResult;
    _statusListener?.call('listening');
  }

  @override
  Future<void> stop() async {}

  @override
  Future<void> cancel() async {}

  void emitResult(String text, {required bool isFinal}) =>
      _resultListener?.call(SpeechUpdate(text, isFinal: isFinal));

  void emitStatus(String status) => _statusListener?.call(status);

  void emitError(SpeechFailureKind kind) =>
      _errorListener?.call(SpeechEngineFailure(kind));
}
