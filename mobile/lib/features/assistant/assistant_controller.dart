import 'package:flutter/foundation.dart';

import '../../core/storage/local_database.dart';
import '../../models/assistant_message.dart';
import 'local_intent.dart';
import 'local_intent_parser.dart';

class AssistantController extends ChangeNotifier {
  AssistantController(this._parser, this._database);

  final LocalIntentParser _parser;
  final LocalDatabase _database;
  final List<AssistantMessage> _messages = [];
  int _sequence = 0;

  List<AssistantMessage> get messages => List.unmodifiable(_messages);

  Future<void> initialize() async {
    try {
      _messages
        ..clear()
        ..addAll(await _database.readMessages());
    } on Object {
      // SQLite is available on the target mobile platforms. Keeping the chat
      // usable in unsupported test/desktop environments is intentional.
    }
    if (_messages.isEmpty) {
      final welcome = _message(
        role: AssistantRole.system,
        text: 'Selecciona un proyecto y un modelo. Luego escribe una instrucción.',
        status: AssistantMessageStatus.sent,
        isOffline: false,
      );
      _messages.add(welcome);
      await _persist(welcome);
    }
    notifyListeners();
  }

  Future<void> submit(String text, {required bool isOffline}) async {
    final cleanText = text.trim();
    if (cleanText.isEmpty) return;
    final userMessage = _message(
      role: AssistantRole.user,
      text: cleanText,
      status: AssistantMessageStatus.sent,
      isOffline: isOffline,
    );
    _messages.add(userMessage);
    await _persist(userMessage);

    final intent = _parser.parse(cleanText);
    final understood = intent.type != LocalIntentType.unknown;
    final answer = understood
        ? 'Comando reconocido: ${intent.description}.\n'
              'La ejecución estará disponible en una siguiente etapa.'
        : 'No pude interpretar la instrucción.\n'
              'Puedes pedirme crear clases, atributos, operaciones, relaciones, '
              'validar o generar.';
    final assistantMessage = _message(
      role: AssistantRole.assistant,
      text: answer,
      status: understood
          ? AssistantMessageStatus.recognized
          : AssistantMessageStatus.notUnderstood,
      isOffline: isOffline,
    );
    _messages.add(assistantMessage);
    await _persist(assistantMessage);
    notifyListeners();
  }

  AssistantMessage _message({
    required AssistantRole role,
    required String text,
    required AssistantMessageStatus status,
    required bool isOffline,
  }) {
    final now = DateTime.now().toUtc();
    _sequence += 1;
    return AssistantMessage(
      id: '${now.microsecondsSinceEpoch}-$_sequence',
      role: role,
      text: text,
      createdAt: now,
      status: status,
      isOffline: isOffline,
    );
  }

  Future<void> _persist(AssistantMessage message) async {
    try {
      await _database.saveMessage(message);
    } on Object {
      // Do not make the assistant unusable if local storage is unavailable.
    }
  }
}
