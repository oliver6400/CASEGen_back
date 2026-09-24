enum LocalIntentType {
  createClass,
  createInterface,
  addAttribute,
  addOperation,
  createRelation,
  validateModel,
  createVersion,
  generateBackend,
  testBackend,
  sync,
  unknown,
}

class LocalIntent {
  const LocalIntent(this.type, {this.parameters = const {}});

  final LocalIntentType type;
  final Map<String, String> parameters;

  String get description {
    final entityName = parameters['entityName'];
    final className = parameters['className'];
    final attributeName = parameters['attributeName'];
    final operationName = parameters['operationName'];
    return switch (type) {
      LocalIntentType.createClass => 'Crear clase $entityName',
      LocalIntentType.createInterface => 'Crear interfaz $entityName',
      LocalIntentType.addAttribute =>
        'Agregar atributo $attributeName a $className',
      LocalIntentType.addOperation =>
        'Agregar operación $operationName a $className',
      LocalIntentType.createRelation => 'Crear relación',
      LocalIntentType.validateModel => 'Validar modelo',
      LocalIntentType.createVersion => 'Crear versión',
      LocalIntentType.generateBackend => 'Generar backend',
      LocalIntentType.testBackend => 'Probar backend',
      LocalIntentType.sync => 'Sincronizar',
      LocalIntentType.unknown => 'Desconocido',
    };
  }
}
