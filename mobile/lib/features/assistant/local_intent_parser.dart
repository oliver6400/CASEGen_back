import 'local_intent.dart';

class LocalIntentParser {
  const LocalIntentParser();

  LocalIntent parse(String input) {
    final original = input.trim().replaceAll(RegExp(r'\s+'), ' ');
    if (original.isEmpty) return const LocalIntent(LocalIntentType.unknown);
    final normalized = _normalize(original);

    final classMatch = RegExp(r'^(?:crea|crear)\s+(?:una?\s+)?clase\s+(.+)$')
        .firstMatch(normalized);
    if (classMatch != null) {
      return LocalIntent(
        LocalIntentType.createClass,
        parameters: {'entityName': _originalGroup(original, classMatch, 1)},
      );
    }

    final interfaceMatch = RegExp(
      r'^(?:crea|crear)\s+(?:una?\s+)?interfaz\s+(.+)$',
    ).firstMatch(normalized);
    if (interfaceMatch != null) {
      return LocalIntent(
        LocalIntentType.createInterface,
        parameters: {'entityName': _originalGroup(original, interfaceMatch, 1)},
      );
    }

    final attributeMatch = RegExp(
      r'^(?:agrega|anade)\s+(?:el\s+atributo\s+)?(\S+)\s+(\S+)\s+a\s+(.+)$',
    ).firstMatch(normalized);
    if (attributeMatch != null) {
      return LocalIntent(
        LocalIntentType.addAttribute,
        parameters: {
          'attributeName': _originalGroup(original, attributeMatch, 1),
          'type': _originalGroup(original, attributeMatch, 2),
          'className': _originalGroup(original, attributeMatch, 3),
        },
      );
    }

    final operationMatch = RegExp(
      r'^(?:agrega|anade)\s+(?:(?:la\s+)?operacion\s+)?(\S+)\s+a\s+(.+)$',
    ).firstMatch(normalized);
    if (operationMatch != null) {
      return LocalIntent(
        LocalIntentType.addOperation,
        parameters: {
          'operationName': _originalGroup(original, operationMatch, 1),
          'className': _originalGroup(original, operationMatch, 2),
        },
      );
    }

    if (RegExp(r'^(?:crea|crear)\s+(?:una?\s+)?(?:relacion|asociacion)\b')
        .hasMatch(normalized)) {
      return const LocalIntent(LocalIntentType.createRelation);
    }
    if (RegExp(r'^(?:valida|validar)\s+(?:el\s+)?modelo$')
        .hasMatch(normalized)) {
      return const LocalIntent(LocalIntentType.validateModel);
    }
    if (RegExp(r'^(?:crea|crear)\s+(?:una?\s+)?version$')
        .hasMatch(normalized)) {
      return const LocalIntent(LocalIntentType.createVersion);
    }
    if (RegExp(r'^(?:genera|generar)\s+(?:el\s+)?backend$')
        .hasMatch(normalized)) {
      return const LocalIntent(LocalIntentType.generateBackend);
    }
    if (RegExp(r'^(?:prueba|probar|testea|testear)\s+(?:el\s+)?backend$')
        .hasMatch(normalized)) {
      return const LocalIntent(LocalIntentType.testBackend);
    }
    if (RegExp(r'^(?:sincroniza|sincronizar|sync)(?:\s+cambios)?$')
        .hasMatch(normalized)) {
      return const LocalIntent(LocalIntentType.sync);
    }
    return const LocalIntent(LocalIntentType.unknown);
  }

  String _normalize(String value) => value
      .toLowerCase()
      .replaceAll('á', 'a')
      .replaceAll('é', 'e')
      .replaceAll('í', 'i')
      .replaceAll('ó', 'o')
      .replaceAll('ú', 'u')
      .replaceAll('ü', 'u')
      .replaceAll('ñ', 'n')
      .trim();

  String _originalGroup(String original, RegExpMatch match, int group) {
    final normalizedOriginal = _normalize(original);
    var cursor = match.start;
    for (var currentGroup = 1; currentGroup <= group; currentGroup += 1) {
      final captured = match.group(currentGroup)!;
      final start = normalizedOriginal.indexOf(captured, cursor);
      if (currentGroup == group) {
        return original.substring(start, start + captured.length).trim();
      }
      cursor = start + captured.length;
    }
    throw StateError('Capture group not found.');
  }
}
