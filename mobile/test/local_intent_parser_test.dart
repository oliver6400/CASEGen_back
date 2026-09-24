import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/assistant/local_intent.dart';
import 'package:mobile/features/assistant/local_intent_parser.dart';

void main() {
  const parser = LocalIntentParser();

  test('recognizes class and preserves its original name', () {
    final result = parser.parse('  crea una clase ClientePreferente  ');
    expect(result.type, LocalIntentType.createClass);
    expect(result.parameters['entityName'], 'ClientePreferente');
  });

  test('recognizes an interface', () {
    final result = parser.parse('crea una interfaz Pagable');
    expect(result.type, LocalIntentType.createInterface);
    expect(result.parameters['entityName'], 'Pagable');
  });

  test('recognizes an attribute with type and owner', () {
    final result = parser.parse('agrega nombre String a Cliente');
    expect(result.type, LocalIntentType.addAttribute);
    expect(result.parameters, {
      'attributeName': 'nombre',
      'type': 'String',
      'className': 'Cliente',
    });
  });

  test('normalizes accents for recognition', () {
    final operation = parser.parse('añade la operación registrar a Cliente');
    expect(operation.type, LocalIntentType.addOperation);
    expect(operation.parameters['operationName'], 'registrar');
    expect(operation.parameters['className'], 'Cliente');
  });

  test('recognizes model and backend commands', () {
    expect(
      parser.parse('valida el modelo').type,
      LocalIntentType.validateModel,
    );
    expect(
      parser.parse('crea una versión').type,
      LocalIntentType.createVersion,
    );
    expect(
      parser.parse('genera el backend').type,
      LocalIntentType.generateBackend,
    );
    expect(parser.parse('prueba el backend').type, LocalIntentType.testBackend);
    expect(parser.parse('sincroniza').type, LocalIntentType.sync);
  });

  test('recognizes every phrase required by the voice flow', () {
    final expected = <String, LocalIntentType>{
      'crea una clase Cliente': LocalIntentType.createClass,
      'crea una interfaz Pagable': LocalIntentType.createInterface,
      'agrega nombre String a Cliente': LocalIntentType.addAttribute,
      'agrega calcularTotal a Pedido': LocalIntentType.addOperation,
      'crea asociación entre Cliente y Pedido': LocalIntentType.createRelation,
      'valida el modelo': LocalIntentType.validateModel,
      'crea una versión': LocalIntentType.createVersion,
      'genera el backend': LocalIntentType.generateBackend,
      'sincroniza cambios': LocalIntentType.sync,
    };

    for (final entry in expected.entries) {
      expect(parser.parse(entry.key).type, entry.value, reason: entry.key);
    }
  });

  test('returns unknown without inventing an intent', () {
    expect(
      parser.parse('haz algo con mi sistema').type,
      LocalIntentType.unknown,
    );
  });
}
